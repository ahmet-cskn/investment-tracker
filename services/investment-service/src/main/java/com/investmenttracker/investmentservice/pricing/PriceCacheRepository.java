package com.investmenttracker.investmentservice.pricing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * The `price` cache (one closing price per investment per day) and the `price_sync` marker of when each
 * investment was last refreshed. Plain JDBC rather than JPA entities: these are bulk upserts and two
 * one-line lookups, not objects with behavior.
 */
@Repository
public class PriceCacheRepository {

	private static final int BATCH_SIZE = 1000;

	private final JdbcTemplate jdbcTemplate;

	public PriceCacheRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	/** The latest cached price on or before the day, if there is one. */
	public Optional<BigDecimal> findLatestOnOrBefore(String name, LocalDate date) {
		List<BigDecimal> prices = jdbcTemplate.queryForList(
				"SELECT price FROM price WHERE name = ? AND date <= ? ORDER BY date DESC LIMIT 1", BigDecimal.class, name,
				date);
		return prices.stream().findFirst();
	}

	public Optional<LocalDate> findNewestDate(String name) {
		// max() over no rows is a single row holding NULL
		return Optional.ofNullable(
				jdbcTemplate.queryForObject("SELECT max(date) FROM price WHERE name = ?", LocalDate.class, name));
	}

	/** Inserts the prices, or overwrites the ones already cached for those days. */
	@Transactional
	public void upsertAll(String name, Map<LocalDate, BigDecimal> prices) {
		jdbcTemplate.batchUpdate(
				"INSERT INTO price (name, date, price) VALUES (?, ?, ?) "
						+ "ON CONFLICT (name, date) DO UPDATE SET price = EXCLUDED.price",
				prices.entrySet(), BATCH_SIZE, (statement, price) -> {
					statement.setString(1, name);
					statement.setObject(2, price.getKey());
					statement.setBigDecimal(3, price.getValue());
				});
	}

	public Optional<LocalDate> findSyncedOn(String name) {
		List<LocalDate> days = jdbcTemplate.queryForList("SELECT synced_on FROM price_sync WHERE name = ?",
				LocalDate.class, name);
		return days.stream().findFirst();
	}

	public void markSynced(String name, LocalDate day) {
		jdbcTemplate.update("INSERT INTO price_sync (name, synced_on) VALUES (?, ?) "
				+ "ON CONFLICT (name) DO UPDATE SET synced_on = EXCLUDED.synced_on", name, day);
	}

}
