package com.investmenttracker.investmentservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.investmenttracker.investmentservice.investment.Investment;
import com.investmenttracker.investmentservice.transactionhistory.TransactionHistory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Round-trips the entities through a real PostgreSQL to check that they map to the Liquibase schema:
 * column names, types, precision and nullability. Each test rolls back, so the database stays empty.
 */
@SpringBootTest
@Testcontainers
@Transactional
class EntityPersistenceIT {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

	@PersistenceContext
	private EntityManager entityManager;

	@Test
	void investmentKeepsItsType() {
		Investment investment = new Investment("Ethereum", new BigDecimal("3.5"));
		investment.setInvestmentType("CRYPTO");

		Investment reloaded = saveAndReload(investment);

		assertThat(reloaded.getInvestmentType()).isEqualTo("CRYPTO");
	}

	@Test
	void investmentWithoutATypeIsStillValid() {
		Investment reloaded = saveAndReload(new Investment("Gold", new BigDecimal("5")));

		assertThat(reloaded.getInvestmentType()).isNull();
	}

	@Test
	void transactionHistoryKeepsAllFieldsIncludingNegativeChange() {
		LocalDate date = LocalDate.parse("2026-09-20");
		TransactionHistory entry = new TransactionHistory("Gold", "METAL", new BigDecimal("-2.5"), date);

		TransactionHistory reloaded = saveAndReload(entry);

		assertThat(reloaded.getId()).isNotNull();
		assertThat(reloaded.getName()).isEqualTo("Gold");
		assertThat(reloaded.getInvestmentType()).isEqualTo("METAL");
		assertThat(reloaded.getChange()).isEqualByComparingTo("-2.5");
		assertThat(reloaded.getDate()).isEqualTo(date);
	}

	@Test
	void transactionHistoryKeepsItsWorthAndDoesNotRequireOne() {
		TransactionHistory withWorth = new TransactionHistory("Gold", "METAL", new BigDecimal("2"), LocalDate.parse("2026-09-20"));
		withWorth.setWorth(new BigDecimal("273.406470123456789012"));
		TransactionHistory withoutWorth = new TransactionHistory("Silver", "METAL", new BigDecimal("2"), LocalDate.parse("2026-09-20"));

		assertThat(saveAndReload(withWorth).getWorth()).isEqualByComparingTo("273.406470123456789012");
		assertThat(saveAndReload(withoutWorth).getWorth()).isNull();
	}

	// Nulling one field at a time (each invocation gets its own rolled-back transaction, as a failed flush poisons it)
	@ParameterizedTest
	@ValueSource(strings = { "name", "investment_type", "change", "date" })
	void transactionHistoryRequiresEveryField(String missingColumn) {
		TransactionHistory incomplete = new TransactionHistory(
				missingColumn.equals("name") ? null : "Gold",
				missingColumn.equals("investment_type") ? null : "METAL",
				missingColumn.equals("change") ? null : new BigDecimal("1"),
				missingColumn.equals("date") ? null : LocalDate.now());

		assertThatThrownBy(() -> {
			entityManager.persist(incomplete);
			entityManager.flush();
		}).rootCause().hasMessageContaining("\"" + missingColumn + "\"").hasMessageContaining("not-null");
	}

	// Flushing and clearing forces the reload to come from the database and not the persistence context
	private <T> T saveAndReload(T entity) {
		entityManager.persist(entity);
		entityManager.flush();
		entityManager.clear();
		Object id = entityManager.getEntityManagerFactory().getPersistenceUnitUtil().getIdentifier(entity);
		@SuppressWarnings("unchecked")
		Class<T> type = (Class<T>) entity.getClass();
		return entityManager.find(type, id);
	}

}
