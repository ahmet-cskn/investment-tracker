package com.investmenttracker.investmentservice.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.investmenttracker.investmentservice.catalog.AssetType;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AlphaVantagePriceProviderTest {

	private static final String KEY = "SECRET-KEY-123";

	private MockRestServiceServer server;
	private AlphaVantagePriceProvider provider;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl("https://alphavantage.test");
		server = MockRestServiceServer.bindTo(builder).build();
		provider = new AlphaVantagePriceProvider(builder.build(), KEY, Duration.ZERO);
	}

	// reset() so a test can make several requests in a row: the mock server refuses new expectations after one is made
	private void respondWith(String json) {
		server.reset();
		server.expect(method(HttpMethod.GET)).andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
	}

	@Test
	void metalsAreRequestedDailyAndConvertedFromTroyOuncesToGrams() {
		server.expect(requestTo(Matchers.startsWith("https://alphavantage.test/query")))
				.andExpect(queryParam("function", "GOLD_SILVER_HISTORY"))
				.andExpect(queryParam("symbol", "SILVER"))
				.andExpect(queryParam("interval", "daily"))
				.andExpect(queryParam("apikey", KEY))
				.andRespond(withSuccess("""
						{"nominal": "XAGUSD", "data": [
						  {"date": "2026-09-23", "price": "31.1034768"},
						  {"date": "2026-09-22", "price": "62.2069536"}]}
						""", MediaType.APPLICATION_JSON));

		Map<LocalDate, BigDecimal> prices = provider.fetchDailyPrices(AssetType.METAL, "SILVER");

		// one troy ounce is 31.1034768 g, so a price of exactly that per ounce is exactly 1 per gram
		assertThat(prices).hasSize(2);
		assertThat(prices.get(LocalDate.parse("2026-09-23"))).isEqualByComparingTo("1");
		assertThat(prices.get(LocalDate.parse("2026-09-22"))).isEqualByComparingTo("2");
		server.verify();
	}

	@Test
	void aRealisticGoldPriceIsConvertedToPerGram() {
		respondWith("""
				{"nominal": "XAUUSD", "data": [{"date": "2026-09-23", "price": "4252.50"}]}
				""");

		BigDecimal perGram = provider.fetchDailyPrices(AssetType.METAL, "GOLD").get(LocalDate.parse("2026-09-23"));

		// 4252.50 / 31.1034768 = 136.7...
		assertThat(perGram).isBetween(new BigDecimal("136.7"), new BigDecimal("136.8"));
		assertThat(perGram.scale()).isEqualTo(18);
	}

	@Test
	void stocksAreRequestedAsTheFreeTiersRecentHistoryAndReadAtTheirClose() {
		server.expect(queryParam("function", "TIME_SERIES_DAILY"))
				.andExpect(queryParam("symbol", "SPY"))
				.andExpect(queryParam("outputsize", "compact"))
				.andExpect(queryParam("apikey", KEY))
				.andRespond(withSuccess("""
						{"Meta Data": {"2. Symbol": "SPY"},
						 "Time Series (Daily)": {
						  "2026-09-23": {"1. open": "500.0", "2. high": "512.0", "3. low": "499.0", "4. close": "510.25", "5. volume": "100"},
						  "2026-09-22": {"1. open": "498.0", "2. high": "505.0", "3. low": "497.0", "4. close": "501.5", "5. volume": "90"}}}
						""", MediaType.APPLICATION_JSON));

		Map<LocalDate, BigDecimal> prices = provider.fetchDailyPrices(AssetType.STOCK, "SPY");

		assertThat(prices).containsOnlyKeys(LocalDate.parse("2026-09-23"), LocalDate.parse("2026-09-22"));
		assertThat(prices.get(LocalDate.parse("2026-09-23"))).isEqualByComparingTo("510.25");
		assertThat(prices.get(LocalDate.parse("2026-09-22"))).isEqualByComparingTo("501.5");
		server.verify();
	}

	@Test
	void cryptoIsRequestedInUsdAndReadAtItsCloseWhateverTheFieldIsCalled() {
		server.expect(queryParam("function", "DIGITAL_CURRENCY_DAILY"))
				.andExpect(queryParam("symbol", "BTC"))
				.andExpect(queryParam("market", "USD"))
				.andRespond(withSuccess("""
						{"Time Series (Digital Currency Daily)": {
						  "2026-09-23": {"1. open": "60000", "2. high": "61000", "3. low": "59000", "4. close": "60500.5", "5. volume": "1"},
						  "2026-09-22": {"1a. open (USD)": "58000", "2a. high (USD)": "59000", "3a. low (USD)": "57000", "4a. close (USD)": "58500.25", "5. volume": "1"}}}
						""", MediaType.APPLICATION_JSON));

		Map<LocalDate, BigDecimal> prices = provider.fetchDailyPrices(AssetType.CRYPTO, "BTC");

		assertThat(prices.get(LocalDate.parse("2026-09-23"))).isEqualByComparingTo("60500.5");
		assertThat(prices.get(LocalDate.parse("2026-09-22"))).isEqualByComparingTo("58500.25");
		server.verify();
	}

	@Test
	void pointsThatCannotBeReadAreSkippedWithoutLosingTheRest() {
		respondWith("""
				{"Time Series (Daily)": {
				  "2026-09-23": {"4. close": "100.5"},
				  "not-a-date": {"4. close": "99"},
				  "2026-09-21": {"4. close": "None"},
				  "2026-09-20": {"4. close": "0"},
				  "2026-09-19": {"4. close": "-5"},
				  "2026-09-18": {"1. open": "10"}}}
				""");

		Map<LocalDate, BigDecimal> prices = provider.fetchDailyPrices(AssetType.STOCK, "SPY");

		assertThat(prices).containsOnlyKeys(LocalDate.parse("2026-09-23"));
	}

	@Test
	void aRateLimitMessageSentAsAnOrdinaryResponseIsAnError() {
		respondWith("""
				{"Information": "Thank you for using Alpha Vantage! Our standard API rate limit is 25 requests per day."}
				""");

		assertThatThrownBy(() -> provider.fetchDailyPrices(AssetType.STOCK, "SPY"))
				.isInstanceOf(PriceUnavailableException.class)
				.hasMessageContaining("25 requests per day");
	}

	@Test
	void anErrorMessageAndANoteAreErrorsToo() {
		respondWith("""
				{"Error Message": "Invalid API call."}
				""");
		assertThatThrownBy(() -> provider.fetchDailyPrices(AssetType.STOCK, "NOPE"))
				.isInstanceOf(PriceUnavailableException.class)
				.hasMessageContaining("Invalid API call");

		respondWith("""
				{"Note": "Please slow down."}
				""");
		assertThatThrownBy(() -> provider.fetchDailyPrices(AssetType.STOCK, "SPY"))
				.isInstanceOf(PriceUnavailableException.class)
				.hasMessageContaining("Please slow down");
	}

	@Test
	void aVeryLongProviderMessageIsShortened() {
		respondWith("{\"Information\": \"" + "x".repeat(500) + "\"}");

		assertThatThrownBy(() -> provider.fetchDailyPrices(AssetType.STOCK, "SPY"))
				.isInstanceOf(PriceUnavailableException.class)
				.message()
				.hasSizeLessThan(260)
				.endsWith("...");
	}

	@Test
	void aResponseWithoutTheExpectedDataIsAnError() {
		respondWith("{\"Meta Data\": {}}");
		assertThatThrownBy(() -> provider.fetchDailyPrices(AssetType.STOCK, "SPY"))
				.isInstanceOf(PriceUnavailableException.class);

		respondWith("{\"nominal\": \"XAGUSD\"}");
		assertThatThrownBy(() -> provider.fetchDailyPrices(AssetType.METAL, "SILVER"))
				.isInstanceOf(PriceUnavailableException.class);
	}

	@Test
	void anEmptyOrNonJsonResponseIsAnError() {
		respondWith("");
		assertThatThrownBy(() -> provider.fetchDailyPrices(AssetType.STOCK, "SPY"))
				.isInstanceOf(PriceUnavailableException.class);

		respondWith("<html>Bad gateway</html>");
		assertThatThrownBy(() -> provider.fetchDailyPrices(AssetType.STOCK, "SPY"))
				.isInstanceOf(PriceUnavailableException.class);
	}

	@Test
	void aServerErrorIsAnErrorThatNamesTheStatus() {
		server.expect(method(HttpMethod.GET)).andRespond(withServerError());

		assertThatThrownBy(() -> provider.fetchDailyPrices(AssetType.STOCK, "SPY"))
				.isInstanceOf(PriceUnavailableException.class)
				.hasMessageContaining("HTTP 500");
	}

	@Test
	void theApiKeyNeverEndsUpInAnErrorEvenWhenTheClientQuotesTheUrl() {
		// Real client exceptions quote the request URL, which carries the key
		server.expect(method(HttpMethod.GET))
				.andRespond(withException(new IOException("Connection reset: https://alphavantage.test/query?apikey=" + KEY)));

		assertThatThrownBy(() -> provider.fetchDailyPrices(AssetType.STOCK, "SPY"))
				.isInstanceOf(PriceUnavailableException.class)
				.hasMessageNotContaining(KEY)
				.hasNoCause();
	}

	@Test
	void withoutAKeyNothingIsSent() {
		for (String noKey : new String[] { null, "", "   " }) {
			RestClient.Builder builder = RestClient.builder().baseUrl("https://alphavantage.test");
			// no expectations are set, so the mock server rejects any request that arrives
			MockRestServiceServer noRequestsExpected = MockRestServiceServer.bindTo(builder).build();
			AlphaVantagePriceProvider keyless = new AlphaVantagePriceProvider(builder.build(), noKey, Duration.ZERO);

			assertThatThrownBy(() -> keyless.fetchDailyPrices(AssetType.STOCK, "SPY"))
					.isInstanceOf(PriceUnavailableException.class)
					.hasMessageContaining("ALPHAVANTAGE_API_KEY");
			noRequestsExpected.verify();
		}
	}

	@Test
	void requestsAreSpacedApartToStayWithinTheOneRequestPerSecondLimit() {
		RestClient.Builder builder = RestClient.builder().baseUrl("https://alphavantage.test");
		MockRestServiceServer twoRequests = MockRestServiceServer.bindTo(builder).build();
		twoRequests.expect(method(HttpMethod.GET))
				.andRespond(withSuccess("{\"data\": [{\"date\": \"2026-09-23\", \"price\": \"31.1034768\"}]}", MediaType.APPLICATION_JSON));
		twoRequests.expect(method(HttpMethod.GET))
				.andRespond(withSuccess("{\"data\": [{\"date\": \"2026-09-23\", \"price\": \"31.1034768\"}]}", MediaType.APPLICATION_JSON));
		Duration interval = Duration.ofMillis(300);
		AlphaVantagePriceProvider spaced = new AlphaVantagePriceProvider(builder.build(), KEY, interval);

		long start = System.nanoTime();
		spaced.fetchDailyPrices(AssetType.METAL, "GOLD");
		Duration first = Duration.ofNanos(System.nanoTime() - start);
		spaced.fetchDailyPrices(AssetType.METAL, "SILVER");
		Duration both = Duration.ofNanos(System.nanoTime() - start);

		// only lower bounds are asserted, which a slow machine cannot break: the first call is not delayed at all
		// (it would take the whole interval if it were), and the second waits out the rest of the interval
		assertThat(first).isLessThan(interval);
		assertThat(both).isGreaterThanOrEqualTo(interval.minusMillis(20));
		twoRequests.verify();
	}

}
