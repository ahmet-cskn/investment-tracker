package com.investmenttracker.investmentservice.pricing;

import com.investmenttracker.investmentservice.catalog.AssetType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Daily closing prices from Alpha Vantage (https://www.alphavantage.co/documentation/). One call returns an
 * asset's whole history, which is what the price cache wants, with one exception: on the free tier a stock's
 * daily history is limited to its 100 most recent trading days (the full history is premium). Stock prices
 * older than that are therefore only available once the cache has collected them, and are otherwise empty.
 *
 * <p>Alpha Vantage reports problems (an exhausted quota, a bad key, an unknown symbol) as an ordinary
 * HTTP 200 whose JSON body has a "Note", "Information" or "Error Message" field instead of data, so those
 * are checked for explicitly.
 *
 * <p>Besides 25 calls a day, the free tier allows only 1 request per second, and a request made sooner is
 * answered with an error message instead of data. Filling several empty assets in a row would hit that, so
 * requests are made one at a time, each at least a minimum interval after the previous one <em>finished</em>
 * (measured from the start, a big response that takes a second to arrive would let the next request be
 * processed only a fraction of a second after it), blocking the caller for the remainder if need be.
 *
 * <p>The API key travels in the request URL, and HTTP client exceptions quote the URL. To keep the key out of
 * logs and error messages, the causes of such exceptions are deliberately not chained or echoed here.
 */
public class AlphaVantagePriceProvider implements PriceProvider {

	/** Metals are quoted per troy ounce, but this application counts them in grams. */
	static final BigDecimal GRAMS_PER_TROY_OUNCE = new BigDecimal("31.1034768");

	// The close field is named "4. close" for stocks and, depending on the API version, "4a. close (USD)" for crypto
	private static final Pattern CLOSE_FIELD = Pattern.compile("^4[a-z]?\\. close.*");

	private static final int MAX_PROVIDER_MESSAGE_LENGTH = 200;

	/** A little over the 1 request per second the free tier allows. */
	static final Duration DEFAULT_MIN_INTERVAL = Duration.ofMillis(1100);

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final RestClient restClient;
	private final String apiKey;
	private final Duration minInterval;

	// Guarded by this: requests are made one at a time
	private long lastFinishedNanos;
	private boolean requestedBefore;

	public AlphaVantagePriceProvider(RestClient restClient, String apiKey) {
		this(restClient, apiKey, DEFAULT_MIN_INTERVAL);
	}

	/** @param minInterval the shortest time between two requests; tests pass zero to run fast */
	public AlphaVantagePriceProvider(RestClient restClient, String apiKey, Duration minInterval) {
		this.restClient = restClient;
		this.apiKey = apiKey;
		this.minInterval = minInterval;
	}

	@Override
	public Map<LocalDate, BigDecimal> fetchDailyPrices(AssetType assetType, String symbol) {
		if (apiKey == null || apiKey.isBlank()) {
			throw new PriceUnavailableException("No Alpha Vantage API key is configured (ALPHAVANTAGE_API_KEY)");
		}

		JsonNode root = parse(request(assetType, symbol));
		rejectProviderMessages(root);

		return switch (assetType) {
			case METAL -> parseMetal(root);
			case CRYPTO -> parseTimeSeries(root, "Time Series (Digital Currency Daily)");
			case STOCK -> parseTimeSeries(root, "Time Series (Daily)");
		};
	}

	// synchronized for the whole request, waiting included: concurrent callers queue up instead of overlapping
	private synchronized String request(AssetType assetType, String symbol) {
		waitForTurn();
		try {
			return restClient.get().uri(uriBuilder -> {
				uriBuilder.path("/query");
				switch (assetType) {
					case METAL -> uriBuilder.queryParam("function", "GOLD_SILVER_HISTORY")
							.queryParam("symbol", symbol)
							.queryParam("interval", "daily");
					case CRYPTO -> uriBuilder.queryParam("function", "DIGITAL_CURRENCY_DAILY")
							.queryParam("symbol", symbol)
							.queryParam("market", "USD");
					// compact is the 100 most recent trading days; the full history is a premium feature
					case STOCK -> uriBuilder.queryParam("function", "TIME_SERIES_DAILY")
							.queryParam("symbol", symbol)
							.queryParam("outputsize", "compact");
				}
				return uriBuilder.queryParam("apikey", apiKey).build();
			}).retrieve().body(String.class);
		}
		catch (RestClientResponseException e) {
			throw new PriceUnavailableException("Alpha Vantage answered with HTTP " + e.getStatusCode().value());
		}
		catch (RuntimeException e) {
			// Not chained and not quoted: the message of a client exception contains the URL, and so the key
			throw new PriceUnavailableException("Could not reach Alpha Vantage (" + e.getClass().getSimpleName() + ")");
		}
		finally {
			requestedBefore = true;
			lastFinishedNanos = System.nanoTime();
		}
	}

	private void waitForTurn() {
		if (!requestedBefore) {
			return;
		}
		long remainingNanos = minInterval.toNanos() - (System.nanoTime() - lastFinishedNanos);
		if (remainingNanos > 0) {
			try {
				TimeUnit.NANOSECONDS.sleep(remainingNanos);
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new PriceUnavailableException("Interrupted while waiting to call Alpha Vantage");
			}
		}
	}

	private JsonNode parse(String body) {
		if (body == null || body.isBlank()) {
			throw new PriceUnavailableException("Alpha Vantage returned an empty response");
		}
		try {
			return JSON.readTree(body);
		}
		catch (JacksonException e) {
			throw new PriceUnavailableException("Alpha Vantage returned something that is not JSON");
		}
	}

	private void rejectProviderMessages(JsonNode root) {
		for (String field : new String[] { "Error Message", "Note", "Information" }) {
			JsonNode message = root.get(field);
			if (message != null && message.isString()) {
				String text = message.asString();
				if (text.length() > MAX_PROVIDER_MESSAGE_LENGTH) {
					text = text.substring(0, MAX_PROVIDER_MESSAGE_LENGTH) + "...";
				}
				throw new PriceUnavailableException("Alpha Vantage: " + text);
			}
		}
	}

	private Map<LocalDate, BigDecimal> parseMetal(JsonNode root) {
		JsonNode data = root.get("data");
		if (data == null || !data.isArray()) {
			throw new PriceUnavailableException("Alpha Vantage's metal response has no price data");
		}
		Map<LocalDate, BigDecimal> prices = new HashMap<>();
		for (JsonNode point : data) {
			LocalDate date = parseDate(point.path("date").asString(""));
			BigDecimal perOunce = parsePositive(point.path("price").asString(""));
			if (date != null && perOunce != null) {
				prices.put(date, perOunce.divide(GRAMS_PER_TROY_OUNCE, 18, RoundingMode.HALF_EVEN));
			}
		}
		return prices;
	}

	private Map<LocalDate, BigDecimal> parseTimeSeries(JsonNode root, String seriesField) {
		JsonNode series = root.get(seriesField);
		if (series == null || !series.isObject()) {
			throw new PriceUnavailableException("Alpha Vantage's response has no '" + seriesField + "' data");
		}
		Map<LocalDate, BigDecimal> prices = new HashMap<>();
		for (Map.Entry<String, JsonNode> day : series.properties()) {
			LocalDate date = parseDate(day.getKey());
			BigDecimal close = findClose(day.getValue());
			if (date != null && close != null) {
				prices.put(date, close);
			}
		}
		return prices;
	}

	private BigDecimal findClose(JsonNode day) {
		for (Map.Entry<String, JsonNode> field : day.properties()) {
			if (CLOSE_FIELD.matcher(field.getKey()).matches()) {
				return parsePositive(field.getValue().asString(""));
			}
		}
		return null;
	}

	/** A point that cannot be read is skipped: one odd row should not lose a whole history. */
	private static LocalDate parseDate(String text) {
		try {
			return LocalDate.parse(text);
		}
		catch (DateTimeParseException e) {
			return null;
		}
	}

	private static BigDecimal parsePositive(String text) {
		try {
			BigDecimal value = new BigDecimal(text);
			return value.signum() > 0 ? value : null;
		}
		catch (NumberFormatException e) {
			return null;
		}
	}

}
