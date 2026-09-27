package com.investmenttracker.investmentservice.financialtransaction;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.investmenttracker.investmentservice.portfolio.PortfolioRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Full-stack tests against a real PostgreSQL. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class FinancialTransactionApiIT {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private FinancialTransactionRepository financialTransactionRepository;

	@Autowired
	private PortfolioRepository portfolioRepository;

	@BeforeEach
	void cleanDatabase() {
		financialTransactionRepository.deleteAll();
	}

	private FinancialTransaction save(String name, String change, String date) {
		return financialTransactionRepository
				.save(new FinancialTransaction(name, new BigDecimal(change), LocalDate.parse(date)));
	}

	@Test
	void createReturns201WithLocationAndBody() throws Exception {
		mockMvc.perform(post("/api/financial-transactions").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "Groceries", "change": -42.5, "date": "2026-01-15"}
						"""))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", matchesPattern(".*/api/financial-transactions/[0-9a-f-]{36}")))
				.andExpect(jsonPath("$.id").isNotEmpty())
				.andExpect(jsonPath("$.name").value("Groceries"))
				.andExpect(jsonPath("$.change").value(-42.5))
				.andExpect(jsonPath("$.date").value("2026-01-15"))
				// no investment type or worth: this is everyday money, not an investment
				.andExpect(jsonPath("$.investmentType").doesNotExist())
				.andExpect(jsonPath("$.worth").doesNotExist());
	}

	@Test
	void createAcceptsAnyNameAndAZeroChange() throws Exception {
		mockMvc.perform(post("/api/financial-transactions").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "Something not in any catalog", "change": 0, "date": "2026-01-15"}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.change").value(0));
	}

	@Test
	void createRejectsInvalidBodyWithFieldErrors() throws Exception {
		mockMvc.perform(post("/api/financial-transactions").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": " ", "change": null, "date": null}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.title").value("Validation failed"))
				.andExpect(jsonPath("$.errors", hasSize(3)));
	}

	@Test
	void createRejectsANameLongerThanTheColumn() throws Exception {
		mockMvc.perform(post("/api/financial-transactions").contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"" + "x".repeat(256) + "\", \"change\": 1, \"date\": \"2026-01-15\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("name"));
	}

	@Test
	void findAllReturnsEntriesNewestFirstWithTiesByName() throws Exception {
		save("Rent", "-900", "2026-01-01");
		save("Salary", "3000", "2026-03-01");
		save("Coffee", "-3", "2026-03-01");

		mockMvc.perform(get("/api/financial-transactions"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(3)))
				.andExpect(jsonPath("$[0].name").value("Coffee"))
				.andExpect(jsonPath("$[1].name").value("Salary"))
				.andExpect(jsonPath("$[2].name").value("Rent"));
	}

	@Test
	void findByIdReturnsTheTransaction() throws Exception {
		FinancialTransaction saved = save("Rent", "-900", "2026-01-01");

		mockMvc.perform(get("/api/financial-transactions/{id}", saved.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Rent"));
	}

	@Test
	void findByIdReturns404WhenMissing() throws Exception {
		mockMvc.perform(get("/api/financial-transactions/{id}", UUID.randomUUID()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.title").value("Financial transaction not found"));
	}

	@Test
	void findByIdReturns400ForMalformedId() throws Exception {
		mockMvc.perform(get("/api/financial-transactions/{id}", "not-a-uuid")).andExpect(status().isBadRequest());
	}

	@Test
	void updateReplacesEveryField() throws Exception {
		FinancialTransaction saved = save("Rent", "-900", "2026-01-01");

		mockMvc.perform(put("/api/financial-transactions/{id}", saved.getId()).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "Salary", "change": 3000, "date": "2026-02-01"}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Salary"))
				.andExpect(jsonPath("$.change").value(3000))
				.andExpect(jsonPath("$.date").value("2026-02-01"));

		mockMvc.perform(get("/api/financial-transactions/{id}", saved.getId()))
				.andExpect(jsonPath("$.name").value("Salary"));
	}

	@Test
	void updateReturns404WhenMissing() throws Exception {
		mockMvc.perform(put("/api/financial-transactions/{id}", UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "Salary", "change": 1, "date": "2026-02-01"}
						"""))
				.andExpect(status().isNotFound());
	}

	@Test
	void updateRejectsInvalidBody() throws Exception {
		FinancialTransaction saved = save("Rent", "-900", "2026-01-01");

		mockMvc.perform(put("/api/financial-transactions/{id}", saved.getId()).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "", "change": 1, "date": "2026-02-01"}
						"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	void deleteReturns204ThenTheTransactionIsGone() throws Exception {
		FinancialTransaction saved = save("Rent", "-900", "2026-01-01");

		mockMvc.perform(delete("/api/financial-transactions/{id}", saved.getId())).andExpect(status().isNoContent());
		mockMvc.perform(get("/api/financial-transactions/{id}", saved.getId())).andExpect(status().isNotFound());
	}

	@Test
	void deleteReturns404WhenMissing() throws Exception {
		mockMvc.perform(delete("/api/financial-transactions/{id}", UUID.randomUUID()))
				.andExpect(status().isNotFound());
	}

	@Test
	void doesNotAppearInThePortfolioOrTheInvestmentTransactions() throws Exception {
		save("Gold", "5", "2026-01-01");

		org.assertj.core.api.Assertions.assertThat(portfolioRepository.sumChangesByName()).isEmpty();
		mockMvc.perform(get("/api/transactions")).andExpect(jsonPath("$", hasSize(0)));
		mockMvc.perform(get("/api/portfolio")).andExpect(jsonPath("$", hasSize(0)));
	}

}
