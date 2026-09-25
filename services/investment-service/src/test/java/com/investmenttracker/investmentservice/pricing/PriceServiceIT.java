package com.investmenttracker.investmentservice.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.investmenttracker.investmentservice.catalog.AssetType;
import com.investmenttracker.investmentservice.catalog.UnknownInvestmentNameException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The pricing service wired to a real database and the seeded catalog, with only the provider faked, so
 * the asset mapping, the cache and the once-a-day rule are checked together.
 */
@SpringBootTest
@Testcontainers
class PriceServiceIT {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

	@MockitoBean
	private PriceProvider priceProvider;

	@Autowired
	private PriceService priceService;

	@Autowired
	private PriceCacheRepository priceCache;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private final LocalDate today = LocalDate.now(ZoneOffset.UTC);

	@BeforeEach
	void cleanDatabase() {
		jdbcTemplate.update("DELETE FROM price");
		jdbcTemplate.update("DELETE FROM price_sync");
	}

	private Map<LocalDate, BigDecimal> tenDaysOfGold() {
		Map<LocalDate, BigDecimal> prices = new HashMap<>();
		for (int i = 0; i < 10; i++) {
			prices.put(today.minusDays(i), new BigDecimal("130").add(BigDecimal.valueOf(10 - i)));
		}
		return prices;
	}

	@Test
	void fillsTheCacheOnceAndThenAnswersEveryDayFromIt() {
		when(priceProvider.fetchDailyPrices(AssetType.METAL, "GOLD")).thenReturn(tenDaysOfGold());

		assertThat(priceService.findPrice("Gold", today.minusDays(3)).orElseThrow()).isEqualByComparingTo("137");
		assertThat(priceService.findPrice("Gold", today.minusDays(5)).orElseThrow()).isEqualByComparingTo("135");
		assertThat(priceService.findPrice("Gold", today).orElseThrow()).isEqualByComparingTo("140");

		// the provider allows 25 calls a day: three lookups cost one
		verify(priceProvider, times(1)).fetchDailyPrices(AssetType.METAL, "GOLD");
		assertThat(priceCache.findSyncedOn("Gold")).hasValue(today);
	}

	@Test
	void usesTheAssetTypeAndSymbolOfEachSeededCatalogEntry() {
		when(priceProvider.fetchDailyPrices(any(), anyString())).thenReturn(Map.of());

		priceService.findPrice("Gold", today);
		priceService.findPrice("Silver", today);
		priceService.findPrice("Bitcoin", today);
		priceService.findPrice("Ethereum", today);
		priceService.findPrice("S&P500", today);

		verify(priceProvider).fetchDailyPrices(AssetType.METAL, "GOLD");
		verify(priceProvider).fetchDailyPrices(AssetType.METAL, "SILVER");
		verify(priceProvider).fetchDailyPrices(AssetType.CRYPTO, "BTC");
		verify(priceProvider).fetchDailyPrices(AssetType.CRYPTO, "ETH");
		verify(priceProvider).fetchDailyPrices(AssetType.STOCK, "SPY");
	}

	@Test
	void aFailureGivesNoPriceAndTheNextLookupTriesAgain() {
		when(priceProvider.fetchDailyPrices(AssetType.METAL, "GOLD"))
				.thenThrow(new PriceUnavailableException("rate limit"))
				.thenReturn(tenDaysOfGold());

		assertThat(priceService.findPrice("Gold", today)).isEmpty();
		assertThat(priceCache.findSyncedOn("Gold")).isEmpty();

		assertThat(priceService.findPrice("Gold", today).orElseThrow()).isEqualByComparingTo("140");
		verify(priceProvider, times(2)).fetchDailyPrices(AssetType.METAL, "GOLD");
	}

	@Test
	void aStockDateOlderThanTheFreeTiersRecentWindowHasNoPriceAndCostsNoExtraCall() {
		// The free tier gives a stock only its 100 most recent trading days
		Map<LocalDate, BigDecimal> recentDays = new HashMap<>();
		for (int i = 0; i < 100; i++) {
			recentDays.put(today.minusDays(i), new BigDecimal("500").add(BigDecimal.valueOf(i)));
		}
		when(priceProvider.fetchDailyPrices(AssetType.STOCK, "SPY")).thenReturn(recentDays);

		assertThat(priceService.findPrice("S&P500", today.minusDays(30))).isPresent();
		assertThat(priceService.findPrice("S&P500", today.minusYears(1))).isEmpty();

		verify(priceProvider, times(1)).fetchDailyPrices(AssetType.STOCK, "SPY");
	}

	@Test
	void anUnknownNameFailsWithoutCallingTheProvider() {
		assertThatThrownBy(() -> priceService.findPrice("Dogecoin", today))
				.isInstanceOf(UnknownInvestmentNameException.class);

		verify(priceProvider, never()).fetchDailyPrices(any(), anyString());
	}

}
