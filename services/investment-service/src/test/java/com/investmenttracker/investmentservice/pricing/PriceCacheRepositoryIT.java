package com.investmenttracker.investmentservice.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** The price cache against a real PostgreSQL, since its SQL (upserts, LIMIT, max()) is what is being tested. */
@SpringBootTest
@Testcontainers
class PriceCacheRepositoryIT {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

	@Autowired
	private PriceCacheRepository priceCache;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void cleanDatabase() {
		jdbcTemplate.update("DELETE FROM price");
		jdbcTemplate.update("DELETE FROM price_sync");
	}

	private static Map<LocalDate, BigDecimal> prices(String... dateAndPrice) {
		Map<LocalDate, BigDecimal> prices = new HashMap<>();
		for (int i = 0; i < dateAndPrice.length; i += 2) {
			prices.put(LocalDate.parse(dateAndPrice[i]), new BigDecimal(dateAndPrice[i + 1]));
		}
		return prices;
	}

	@Test
	void findsTheLatestPriceOnOrBeforeADay() {
		// Friday and Monday: nothing was cached for the weekend in between
		priceCache.upsertAll("Gold", prices("2026-09-17", "136", "2026-09-18", "137", "2026-09-21", "138"));

		assertThat(priceCache.findLatestOnOrBefore("Gold", LocalDate.parse("2026-09-18"))).hasValue(new BigDecimal("137").setScale(18));
		assertThat(priceCache.findLatestOnOrBefore("Gold", LocalDate.parse("2026-09-20"))).hasValue(new BigDecimal("137").setScale(18));
		assertThat(priceCache.findLatestOnOrBefore("Gold", LocalDate.parse("2026-09-21"))).hasValue(new BigDecimal("138").setScale(18));
		assertThat(priceCache.findLatestOnOrBefore("Gold", LocalDate.parse("2030-01-01"))).hasValue(new BigDecimal("138").setScale(18));
	}

	@Test
	void hasNoPriceBeforeTheFirstOneOrForAnotherInvestment() {
		priceCache.upsertAll("Gold", prices("2026-09-17", "136"));

		assertThat(priceCache.findLatestOnOrBefore("Gold", LocalDate.parse("2026-09-16"))).isEmpty();
		assertThat(priceCache.findLatestOnOrBefore("Silver", LocalDate.parse("2026-09-17"))).isEmpty();
	}

	@Test
	void upsertingAgainOverwritesThatDayAndKeepsTheOthers() {
		priceCache.upsertAll("Gold", prices("2026-09-17", "136", "2026-09-18", "137"));

		priceCache.upsertAll("Gold", prices("2026-09-18", "999", "2026-09-21", "138"));

		assertThat(priceCache.findLatestOnOrBefore("Gold", LocalDate.parse("2026-09-17"))).hasValue(new BigDecimal("136").setScale(18));
		assertThat(priceCache.findLatestOnOrBefore("Gold", LocalDate.parse("2026-09-18"))).hasValue(new BigDecimal("999").setScale(18));
		assertThat(priceCache.findLatestOnOrBefore("Gold", LocalDate.parse("2026-09-21"))).hasValue(new BigDecimal("138").setScale(18));
		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM price", Integer.class)).isEqualTo(3);
	}

	@Test
	void keepsEighteenDecimalsOfPrecision() {
		priceCache.upsertAll("Gold", prices("2026-09-17", "136.123456789012345678"));

		assertThat(priceCache.findLatestOnOrBefore("Gold", LocalDate.parse("2026-09-17")).orElseThrow())
				.isEqualByComparingTo("136.123456789012345678");
	}

	@Test
	void writesALargeHistoryInBatches() {
		// More than the batch size, like the ~6,000 days of a stock
		Map<LocalDate, BigDecimal> history = new HashMap<>();
		LocalDate day = LocalDate.parse("2000-01-01");
		for (int i = 0; i < 2500; i++) {
			history.put(day.plusDays(i), BigDecimal.valueOf(100 + i));
		}

		priceCache.upsertAll("Silver", history);

		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM price WHERE name = 'Silver'", Integer.class))
				.isEqualTo(2500);
	}

	@Test
	void findsTheNewestCachedDay() {
		assertThat(priceCache.findNewestDate("Gold")).isEmpty();

		priceCache.upsertAll("Gold", prices("2026-09-17", "136", "2026-09-21", "138"));

		assertThat(priceCache.findNewestDate("Gold")).hasValue(LocalDate.parse("2026-09-21"));
		assertThat(priceCache.findNewestDate("Silver")).isEmpty();
	}

	@Test
	void remembersWhenAnInvestmentWasLastRefreshed() {
		assertThat(priceCache.findSyncedOn("Gold")).isEmpty();

		priceCache.markSynced("Gold", LocalDate.parse("2026-09-20"));
		assertThat(priceCache.findSyncedOn("Gold")).hasValue(LocalDate.parse("2026-09-20"));

		priceCache.markSynced("Gold", LocalDate.parse("2026-09-21"));
		assertThat(priceCache.findSyncedOn("Gold")).hasValue(LocalDate.parse("2026-09-21"));
		assertThat(priceCache.findSyncedOn("Silver")).isEmpty();
	}

}
