package com.investmenttracker.investmentservice.portfolio;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.investmenttracker.investmentservice.catalog.AssetType;
import com.investmenttracker.investmentservice.investment.Investment;
import com.investmenttracker.investmentservice.investment.InvestmentRepository;
import com.investmenttracker.investmentservice.pricing.PriceProvider;
import com.investmenttracker.investmentservice.transactionhistory.TransactionHistory;
import com.investmenttracker.investmentservice.transactionhistory.TransactionHistoryRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Full-stack tests against a real PostgreSQL, so the aggregate queries run for real. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PortfolioApiIT {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private InvestmentRepository investmentRepository;

	@Autowired
	private TransactionHistoryRepository transactionHistoryRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	// Only the provider (the network) is faked: the price service, its cache and the database are real.
	// Unstubbed, the mock returns no prices at all, so worth is null unless a test sets a price up.
	@MockitoBean
	private PriceProvider priceProvider;

	@BeforeEach
	void cleanDatabase() {
		investmentRepository.deleteAll();
		transactionHistoryRepository.deleteAll();
		// The price cache and its "refreshed today" marker persist between tests, so a leftover would hide a test's prices
		jdbcTemplate.update("DELETE FROM price");
		jdbcTemplate.update("DELETE FROM price_sync");
	}

	private void goldCosts(String price) {
		// The provider returns whole histories; one day well in the past is enough, since the latest on or before today wins
		doReturn(Map.of(LocalDate.parse("2026-01-02"), new BigDecimal(price))).when(priceProvider)
				.fetchDailyPrices(AssetType.METAL, "GOLD");
	}

	@Test
	void isEmptyWhenThereIsNothing() throws Exception {
		mockMvc.perform(get("/api/portfolio")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void addsTheInitialAmountAndTheTransactionChanges() throws Exception {
		// The example from the spec: 2 + (-1) + 5 = 6
		initial("Gold", "2");
		transaction("Gold", "-1");
		transaction("Gold", "5");

		mockMvc.perform(get("/api/portfolio"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].name").value("Gold"))
				.andExpect(jsonPath("$[0].investmentType").value("Precious Metal"))
				.andExpect(jsonPath("$[0].amount").value(6));
	}

	@Test
	void valuesEachHoldingAtItsLatestPrice() throws Exception {
		goldCosts("100.5");
		initial("Gold", "2");
		transaction("Gold", "-0.5");

		// 1.5 grams at 100.5 each
		mockMvc.perform(get("/api/portfolio"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].amount").value(1.5))
				.andExpect(jsonPath("$[0].worth").value(150.75));
	}

	@Test
	void hasNoWorthForAnInvestmentWithoutAPriceButStillValuesTheOthers() throws Exception {
		goldCosts("100");
		initial("Gold", "3");
		initial("Silver", "10");

		mockMvc.perform(get("/api/portfolio"))
				.andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$[0].name").value("Gold"))
				.andExpect(jsonPath("$[0].worth").value(300))
				.andExpect(jsonPath("$[1].name").value("Silver"))
				.andExpect(jsonPath("$[1].worth").value(nullValue()));
	}

	@Test
	void readsThePricesFromTheCacheOnLaterRequests() throws Exception {
		goldCosts("100");
		initial("Gold", "1");

		mockMvc.perform(get("/api/portfolio")).andExpect(jsonPath("$[0].worth").value(100));
		mockMvc.perform(get("/api/portfolio")).andExpect(jsonPath("$[0].worth").value(100));

		verify(priceProvider, times(1)).fetchDailyPrices(AssetType.METAL, "GOLD");
	}

	@Test
	void combinesSeveralInvestmentsSortedByName() throws Exception {
		initial("Silver", "10");
		initial("Bitcoin", "1");
		transaction("Bitcoin", "0.5");
		transaction("Ethereum", "3");

		mockMvc.perform(get("/api/portfolio"))
				.andExpect(jsonPath("$", hasSize(3)))
				.andExpect(jsonPath("$[0].name").value("Bitcoin"))
				.andExpect(jsonPath("$[0].amount").value(1.5))
				.andExpect(jsonPath("$[0].investmentType").value("Cryptocurrency"))
				.andExpect(jsonPath("$[1].name").value("Ethereum"))
				.andExpect(jsonPath("$[1].amount").value(3))
				.andExpect(jsonPath("$[2].name").value("Silver"))
				.andExpect(jsonPath("$[2].amount").value(10));
	}

	@Test
	void keepsAZeroTotalAndANegativeTotal() throws Exception {
		initial("Gold", "2");
		transaction("Gold", "-2");
		transaction("Silver", "-4");

		mockMvc.perform(get("/api/portfolio"))
				.andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$[0].name").value("Gold"))
				.andExpect(jsonPath("$[0].amount").value(0))
				.andExpect(jsonPath("$[1].name").value("Silver"))
				.andExpect(jsonPath("$[1].amount").value(-4));
	}

	@Test
	void sumsTwoInitialRowsForTheSameName() throws Exception {
		initial("Gold", "2");
		initial("Gold", "3");

		mockMvc.perform(get("/api/portfolio"))
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].amount").value(5));
	}

	@Test
	void takesTheTypeFromTheCatalogNotFromTheStoredRows() throws Exception {
		Investment wrongType = new Investment("Gold", new BigDecimal("2"));
		wrongType.setInvestmentType("Stock");
		investmentRepository.save(wrongType);

		mockMvc.perform(get("/api/portfolio")).andExpect(jsonPath("$[0].investmentType").value("Precious Metal"));
	}

	@Test
	void sumsWithoutLosingPrecision() throws Exception {
		initial("Ethereum", "0.000000000000000001");
		transaction("Ethereum", "0.000000000000000002");

		// Compared as text: a JSON number would be parsed into a double and hide any lost digits
		String body = mockMvc.perform(get("/api/portfolio")).andReturn().getResponse().getContentAsString();
		org.assertj.core.api.Assertions.assertThat(body).contains("\"amount\":3E-18");
	}

	private void initial(String name, String amount) {
		investmentRepository.save(new Investment(name, new BigDecimal(amount)));
	}

	private void transaction(String name, String change) {
		transactionHistoryRepository
				.save(new TransactionHistory(name, "any", new BigDecimal(change), LocalDate.parse("2026-01-01")));
	}

}
