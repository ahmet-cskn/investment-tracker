package com.investmenttracker.investmentservice.transactionhistory;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

	@BeforeEach
	void cleanDatabase() {
		transactionHistoryRepository.deleteAll();
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
