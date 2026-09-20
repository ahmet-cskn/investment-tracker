package com.investmenttracker.investmentservice;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Checks the schema produced by the Liquibase changelog directly, independent of the JPA entities.
 */
@SpringBootTest
@Testcontainers
class DatabaseSchemaIT {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void investmentsTableHasTypeAndWorthColumns() {
		assertThat(columnsOf("investments")).containsExactly(
				Map.entry("id", "uuid NOT NULL"),
				Map.entry("name", "character varying NOT NULL"),
				Map.entry("amount", "numeric(38,18) NOT NULL"),
				Map.entry("investment_type", "character varying NULL"),
				Map.entry("worth", "numeric(38,18) NULL"));
	}

	@Test
	void transactionHistoryTableHasExpectedColumns() {
		assertThat(columnsOf("transaction_history")).containsExactly(
				Map.entry("id", "uuid NOT NULL"),
				Map.entry("name", "character varying NOT NULL"),
				Map.entry("investment_type", "character varying NOT NULL"),
				Map.entry("change", "numeric(38,18) NOT NULL"),
				Map.entry("timestamp", "timestamp with time zone NOT NULL"));
	}

	@Test
	void transactionHistoryAcceptsNegativeChanges() {
		jdbcTemplate.update(
				"INSERT INTO transaction_history (id, name, investment_type, change, timestamp) VALUES (?, ?, ?, ?, now())",
				UUID.randomUUID(), "Gold", "METAL", new BigDecimal("-2.5"));

		BigDecimal stored = jdbcTemplate.queryForObject("SELECT change FROM transaction_history WHERE name = 'Gold'",
				BigDecimal.class);
		assertThat(stored).isEqualByComparingTo("-2.5");
	}

	private Map<String, String> columnsOf(String table) {
		Map<String, String> columns = new LinkedHashMap<>();
		jdbcTemplate.query("""
				SELECT column_name, data_type, is_nullable, numeric_precision, numeric_scale
				FROM information_schema.columns
				WHERE table_schema = 'public' AND table_name = ?
				ORDER BY ordinal_position
				""", rs -> {
			String type = rs.getString("data_type");
			if (type.equals("numeric")) {
				type += "(" + rs.getInt("numeric_precision") + "," + rs.getInt("numeric_scale") + ")";
			}
			columns.put(rs.getString("column_name"), type + (rs.getString("is_nullable").equals("YES") ? " NULL" : " NOT NULL"));
		}, table);
		return columns;
	}

}
