package com.investmenttracker.investmentservice.transactionhistory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.investmenttracker.investmentservice.catalog.AssetType;
import com.investmenttracker.investmentservice.pricing.PriceProvider;
import com.investmenttracker.investmentservice.pricing.PriceUnavailableException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Full-stack tests against a real PostgreSQL, seeded with the catalog by the Liquibase changelog. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class TransactionHistoryApiIT {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private TransactionHistoryRepository transactionHistoryRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	// Only the provider (the network) is faked: the price service, its cache and the database are real.
	// Unstubbed, the mock returns no prices at all, so worth is empty unless a test sets a price up.
	@MockitoBean
	private PriceProvider priceProvider;

	@BeforeEach
	void cleanDatabase() {
		transactionHistoryRepository.deleteAll();
		// The price cache and its "refreshed today" marker persist between tests, so a leftover would hide a test's prices
		jdbcTemplate.update("DELETE FROM price");
		jdbcTemplate.update("DELETE FROM price_sync");
	}

	private void goldCosts(String... dateAndPrice) {
		Map<LocalDate, BigDecimal> prices = new java.util.HashMap<>();
		for (int i = 0; i < dateAndPrice.length; i += 2) {
			prices.put(LocalDate.parse(dateAndPrice[i]), new BigDecimal(dateAndPrice[i + 1]));
		}
		// doReturn rather than when(): the mock may currently be set to throw, and when() would call it while stubbing
		doReturn(prices).when(priceProvider).fetchDailyPrices(AssetType.METAL, "GOLD");
	}

	private void createTransaction(String json) throws Exception {
		mockMvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isCreated());
	}

	@Test
	void createReturns201WithLocationAndDerivedType() throws Exception {
		mockMvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "Bitcoin", "change": -1.5, "date": "2026-01-15"}
						"""))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", matchesPattern(".*/api/transactions/[0-9a-f-]{36}")))
				.andExpect(jsonPath("$.id").isNotEmpty())
				.andExpect(jsonPath("$.name").value("Bitcoin"))
				.andExpect(jsonPath("$.investmentType").value("Cryptocurrency"))
				.andExpect(jsonPath("$.change").value(-1.5))
				.andExpect(jsonPath("$.date").value("2026-01-15"));
	}

	@Test
	void createRejectsAnUnknownInvestmentName() throws Exception {
		mockMvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "Dogecoin", "change": 1, "date": "2026-01-15"}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.title").value("Invalid investment name"))
				.andExpect(jsonPath("$.errors[0].field").value("name"));
	}

	@Test
	void createRejectsInvalidBodyWithFieldErrors() throws Exception {
		mockMvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": " ", "change": null, "date": null}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.title").value("Validation failed"))
				.andExpect(jsonPath("$.errors", hasSize(3)));
	}

	@Test
	void createAcceptsAZeroChange() throws Exception {
		mockMvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "Gold", "change": 0, "date": "2026-01-15"}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.change").value(0));
	}

	@Test
	void findAllReturnsEntriesNewestFirst() throws Exception {
		save("Gold", new BigDecimal("1"), LocalDate.parse("2026-01-01"));
		save("Bitcoin", new BigDecimal("2"), LocalDate.parse("2026-03-01"));
		save("Ethereum", new BigDecimal("3"), LocalDate.parse("2026-02-01"));

		mockMvc.perform(get("/api/transactions"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(3)))
				.andExpect(jsonPath("$[0].name").value("Bitcoin"))
				.andExpect(jsonPath("$[1].name").value("Ethereum"))
				.andExpect(jsonPath("$[2].name").value("Gold"));
	}

	@Test
	void findAllOrdersTransactionsOnTheSameDayByName() throws Exception {
		LocalDate sameDay = LocalDate.parse("2026-01-01");
		save("Silver", new BigDecimal("1"), sameDay);
		save("Bitcoin", new BigDecimal("2"), sameDay);
		save("Gold", new BigDecimal("3"), sameDay);

		mockMvc.perform(get("/api/transactions"))
				.andExpect(jsonPath("$[0].name").value("Bitcoin"))
				.andExpect(jsonPath("$[1].name").value("Gold"))
				.andExpect(jsonPath("$[2].name").value("Silver"));
	}

	@Test
	void createStoresTheWorthFromThePriceOfTheDay() throws Exception {
		goldCosts("2026-01-14", "99", "2026-01-15", "100.5");

		mockMvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "Gold", "change": 2, "date": "2026-01-15"}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.worth").value(201.0));

		// and it is what was persisted, not only what was returned
		assertThat(transactionHistoryRepository.findAll().get(0).getWorth()).isEqualByComparingTo("201");
	}

	@Test
	void aDecreaseIsWorthANegativeAmount() throws Exception {
		goldCosts("2026-01-15", "100");

		mockMvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "Gold", "change": -1.5, "date": "2026-01-15"}
						"""))
				.andExpect(jsonPath("$.worth").value(-150.0));
	}

	@Test
	void aDayWithoutAPriceUsesTheLatestOneBeforeIt() throws Exception {
		// 2026-01-17 is a Saturday: the last price is Friday's
		goldCosts("2026-01-15", "100", "2026-01-16", "110");

		mockMvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "Gold", "change": 1, "date": "2026-01-17"}
						"""))
				.andExpect(jsonPath("$.worth").value(110.0));
	}

	@Test
	void aDayBeforeAnyPriceExistsIsSavedWithoutAWorth() throws Exception {
		goldCosts("2026-01-15", "100");

		mockMvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "Gold", "change": 1, "date": "2010-01-01"}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.worth").value(nullValue()));
	}

	@Test
	void aProviderFailureStillSavesTheTransactionWithoutAWorth() throws Exception {
		when(priceProvider.fetchDailyPrices(any(), anyString()))
				.thenThrow(new PriceUnavailableException("Alpha Vantage: rate limit"));

		mockMvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "Gold", "change": 2, "date": "2026-01-15"}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.name").value("Gold"))
				.andExpect(jsonPath("$.worth").value(nullValue()));

		assertThat(transactionHistoryRepository.findAll()).hasSize(1);
	}

	@Test
	void theProviderIsCalledOncePerAssetNoMatterHowManyTransactions() throws Exception {
		goldCosts("2026-01-14", "99", "2026-01-15", "100");

		createTransaction("""
				{"name": "Gold", "change": 1, "date": "2026-01-15"}
				""");
		createTransaction("""
				{"name": "Gold", "change": 2, "date": "2026-01-14"}
				""");
		createTransaction("""
				{"name": "Gold", "change": 3, "date": "2026-01-15"}
				""");

		verify(priceProvider, times(1)).fetchDailyPrices(AssetType.METAL, "GOLD");
		assertThat(transactionHistoryRepository.findAll())
				.extracting(entry -> entry.getWorth().stripTrailingZeros().toPlainString())
				.containsExactlyInAnyOrder("100", "198", "300");
	}

	@Test
	void findAllAndFindByIdIncludeTheWorth() throws Exception {
		goldCosts("2026-01-15", "100");
		createTransaction("""
				{"name": "Gold", "change": 2, "date": "2026-01-15"}
				""");
		TransactionHistory saved = transactionHistoryRepository.findAll().get(0);

		mockMvc.perform(get("/api/transactions")).andExpect(jsonPath("$[0].worth").value(200.0));
		mockMvc.perform(get("/api/transactions/{id}", saved.getId())).andExpect(jsonPath("$.worth").value(200.0));
	}

	@Test
	void updateWorksTheWorthOutAgain() throws Exception {
		goldCosts("2026-01-15", "100", "2026-02-01", "150");
		createTransaction("""
				{"name": "Gold", "change": 2, "date": "2026-01-15"}
				""");
		TransactionHistory saved = transactionHistoryRepository.findAll().get(0);

		mockMvc.perform(put("/api/transactions/{id}", saved.getId()).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "Gold", "change": 4, "date": "2026-02-01"}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.worth").value(600.0));

		assertThat(transactionHistoryRepository.findById(saved.getId()).orElseThrow().getWorth())
				.isEqualByComparingTo("600");
	}

	@Test
	void savingAgainFillsInAWorthThatWasMissing() throws Exception {
		// first the provider is down...
		when(priceProvider.fetchDailyPrices(any(), anyString()))
				.thenThrow(new PriceUnavailableException("Alpha Vantage: rate limit"));
		createTransaction("""
				{"name": "Gold", "change": 2, "date": "2026-01-15"}
				""");
		TransactionHistory saved = transactionHistoryRepository.findAll().get(0);
		assertThat(saved.getWorth()).isNull();

		// ...and then it is back, so simply saving the transaction again gets its worth
		goldCosts("2026-01-15", "100");
		mockMvc.perform(put("/api/transactions/{id}", saved.getId()).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "Gold", "change": 2, "date": "2026-01-15"}
						"""))
				.andExpect(jsonPath("$.worth").value(200.0));
	}

	@Test
	void updateClearsTheWorthWhenTheNewDayHasNoPrice() throws Exception {
		goldCosts("2026-01-15", "100");
		createTransaction("""
				{"name": "Gold", "change": 2, "date": "2026-01-15"}
				""");
		TransactionHistory saved = transactionHistoryRepository.findAll().get(0);

		mockMvc.perform(put("/api/transactions/{id}", saved.getId()).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "Gold", "change": 2, "date": "2010-01-01"}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.worth").value(nullValue()));
	}

	@Test
	void findByIdReturnsEntry() throws Exception {
		TransactionHistory saved = save("Gold", new BigDecimal("2.5"), LocalDate.parse("2026-01-01"));

		mockMvc.perform(get("/api/transactions/{id}", saved.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Gold"))
				.andExpect(jsonPath("$.investmentType").value("Precious Metal"))
				.andExpect(jsonPath("$.change").value(2.5));
	}

	@Test
	void findByIdReturns404WhenMissing() throws Exception {
		mockMvc.perform(get("/api/transactions/{id}", UUID.randomUUID()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.title").value("Transaction not found"));
	}

	@Test
	void updateReplacesEveryFieldAndRederivesType() throws Exception {
		TransactionHistory saved = save("Gold", new BigDecimal("1"), LocalDate.parse("2026-01-01"));

		mockMvc.perform(put("/api/transactions/{id}", saved.getId()).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "Bitcoin", "change": -2.5, "date": "2026-02-01"}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Bitcoin"))
				.andExpect(jsonPath("$.investmentType").value("Cryptocurrency"))
				.andExpect(jsonPath("$.change").value(-2.5))
				.andExpect(jsonPath("$.date").value("2026-02-01"));
	}

	@Test
	void updateRejectsAnUnknownInvestmentName() throws Exception {
		TransactionHistory saved = save("Gold", new BigDecimal("1"), LocalDate.now());

		mockMvc.perform(put("/api/transactions/{id}", saved.getId()).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "Dogecoin", "change": 1, "date": "2026-01-15"}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.title").value("Invalid investment name"));
	}

	@Test
	void updateReturns404WhenMissing() throws Exception {
		mockMvc.perform(put("/api/transactions/{id}", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "Gold", "change": 1, "date": "2026-01-15"}
						"""))
				.andExpect(status().isNotFound());
	}

	@Test
	void deleteRemovesEntry() throws Exception {
		TransactionHistory saved = save("Gold", new BigDecimal("1"), LocalDate.now());

		mockMvc.perform(delete("/api/transactions/{id}", saved.getId())).andExpect(status().isNoContent());

		mockMvc.perform(get("/api/transactions/{id}", saved.getId())).andExpect(status().isNotFound());
	}

	@Test
	void deleteReturns404WhenMissing() throws Exception {
		mockMvc.perform(delete("/api/transactions/{id}", UUID.randomUUID())).andExpect(status().isNotFound());
	}

	private TransactionHistory save(String name, BigDecimal change, LocalDate date) {
		String investmentType = switch (name) {
			case "Gold", "Silver" -> "Precious Metal";
			case "Bitcoin", "Ethereum" -> "Cryptocurrency";
			default -> "Stock";
		};
		return transactionHistoryRepository.save(new TransactionHistory(name, investmentType, change, date));
	}

}
