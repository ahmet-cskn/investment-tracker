package com.investmenttracker.investmentservice.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import com.investmenttracker.investmentservice.catalog.AssetType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;

/**
 * Checks the client against the real Alpha Vantage, which nothing else can: that its response formats are
 * what the parser expects, that the history is deep enough, and that metals really come out per gram.
 *
 * <p>Skipped unless ALPHAVANTAGE_API_KEY is set (so CI never runs it), and each run uses 5 of the free tier's
 * 25 calls per day, spaced out by the provider itself (1 request per second). Run it with the key in the environment: {@code mvn verify -Dit.test=AlphaVantageLiveIT}.
 */
@EnabledIfEnvironmentVariable(named = "ALPHAVANTAGE_API_KEY", matches = ".+")
class AlphaVantageLiveIT {

	// Static: JUnit makes a new test object per test method, and the provider's spacing of requests (the free tier
	// allows one per second) only works if every test shares one provider, as the application does
	private static final AlphaVantagePriceProvider provider = new AlphaVantagePriceProvider(
			RestClient.builder().baseUrl("https://www.alphavantage.co").build(), System.getenv("ALPHAVANTAGE_API_KEY"));

	private final LocalDate today = LocalDate.now(ZoneOffset.UTC);

	private Map<LocalDate, BigDecimal> fetch(AssetType type, String symbol) {
		Map<LocalDate, BigDecimal> prices = provider.fetchDailyPrices(type, symbol);
		LocalDate oldest = Collections.min(prices.keySet());
		LocalDate newest = Collections.max(prices.keySet());
		System.out.printf("%s (%s): %d days, %s to %s%n", symbol, type, prices.size(), oldest, newest);
		// up to date (a weekend or holiday can leave the newest a few days old)
		assertThat(newest).isAfterOrEqualTo(today.minusDays(7));
		return prices;
	}

	private Map<LocalDate, BigDecimal> fetchWithFiveYears(AssetType type, String symbol) {
		Map<LocalDate, BigDecimal> prices = fetch(type, symbol);
		assertThat(Collections.min(prices.keySet())).isBeforeOrEqualTo(today.minusYears(5));
		return prices;
	}

	private BigDecimal newest(Map<LocalDate, BigDecimal> prices) {
		return prices.get(Collections.max(prices.keySet()));
	}

	@Test
	void goldAndSilverComeOutPerGram() {
		BigDecimal gold = newest(fetchWithFiveYears(AssetType.METAL, "GOLD"));
		BigDecimal silver = newest(fetchWithFiveYears(AssetType.METAL, "SILVER"));

		// Per troy ounce gold has been in the thousands of dollars and silver in the tens; per gram they are ~31x less
		assertThat(gold).isBetween(BigDecimal.valueOf(20), BigDecimal.valueOf(500));
		assertThat(silver).isBetween(BigDecimal.valueOf(0.2), BigDecimal.valueOf(20));
	}

	@Test
	void cryptoIsReadAtItsClose() {
		assertThat(newest(fetchWithFiveYears(AssetType.CRYPTO, "BTC"))).isGreaterThan(BigDecimal.valueOf(1000));
		assertThat(newest(fetchWithFiveYears(AssetType.CRYPTO, "ETH"))).isGreaterThan(BigDecimal.valueOf(50));
	}

	@Test
	void theSp500IsTrackedThroughSpyForTheFreeTiersRecentDays() {
		Map<LocalDate, BigDecimal> prices = fetch(AssetType.STOCK, "SPY");

		// The free tier's daily history for a stock is its 100 most recent trading days, not more
		assertThat(prices).hasSizeBetween(90, 110);
		assertThat(newest(prices)).isBetween(BigDecimal.valueOf(100), BigDecimal.valueOf(3000));
	}

}
