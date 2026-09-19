package com.investmenttracker.investmentservice.investment;

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

/**
 * Full-stack tests against a real PostgreSQL. Liquibase creates the schema and Hibernate validates it,
 * so a mismatch between the changelog and the entity fails here.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class InvestmentApiIT {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private InvestmentRepository investmentRepository;

	@BeforeEach
	void cleanDatabase() {
		investmentRepository.deleteAll();
	}

	@Test
	void createReturns201WithLocationAndBody() throws Exception {
		mockMvc.perform(post("/api/investments").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "Ethereum", "amount": 3.5}
						"""))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", matchesPattern(".*/api/investments/[0-9a-f-]{36}")))
				.andExpect(jsonPath("$.id").isNotEmpty())
				.andExpect(jsonPath("$.name").value("Ethereum"))
				.andExpect(jsonPath("$.amount").value(3.5));
	}

	@Test
	void createKeepsHighPrecisionAmounts() throws Exception {
		mockMvc.perform(post("/api/investments").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "Ethereum", "amount": 0.000000000000000001}
						"""))
				.andExpect(status().isCreated());

		Investment saved = investmentRepository.findAll().get(0);
		org.assertj.core.api.Assertions.assertThat(saved.getAmount())
				.isEqualByComparingTo(new BigDecimal("0.000000000000000001"));
	}

	@Test
	void createRejectsInvalidBodyWithFieldErrors() throws Exception {
		mockMvc.perform(post("/api/investments").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": " ", "amount": -1}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.title").value("Validation failed"))
				.andExpect(jsonPath("$.errors", hasSize(2)));
	}

	@Test
	void createRejectsAmountThatExceedsColumnPrecision() throws Exception {
		mockMvc.perform(post("/api/investments").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "Ethereum", "amount": 0.0000000000000000001}
						"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	void createRejectsMalformedJson() throws Exception {
		mockMvc.perform(post("/api/investments").contentType(MediaType.APPLICATION_JSON).content("{not json"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void findAllReturnsStoredInvestments() throws Exception {
		investmentRepository.save(new Investment("Gold", new BigDecimal("5")));
		investmentRepository.save(new Investment("Ethereum", new BigDecimal("3.5")));

		mockMvc.perform(get("/api/investments"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(2)));
	}

	@Test
	void findByIdReturnsInvestment() throws Exception {
		Investment saved = investmentRepository.save(new Investment("Gold", new BigDecimal("5")));

		mockMvc.perform(get("/api/investments/{id}", saved.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(saved.getId().toString()))
				.andExpect(jsonPath("$.name").value("Gold"))
				.andExpect(jsonPath("$.amount").value(5));
	}

	@Test
	void findByIdReturns404WhenMissing() throws Exception {
		mockMvc.perform(get("/api/investments/{id}", UUID.randomUUID()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.title").value("Investment not found"));
	}

	@Test
	void findByIdReturns400ForMalformedId() throws Exception {
		mockMvc.perform(get("/api/investments/{id}", "not-a-uuid")).andExpect(status().isBadRequest());
	}

	@Test
	void updateReplacesNameAndAmount() throws Exception {
		Investment saved = investmentRepository.save(new Investment("Gold", new BigDecimal("5")));

		mockMvc.perform(put("/api/investments/{id}", saved.getId()).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "Silver", "amount": 12.5}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Silver"))
				.andExpect(jsonPath("$.amount").value(12.5));

		mockMvc.perform(get("/api/investments/{id}", saved.getId()))
				.andExpect(jsonPath("$.name").value("Silver"));
	}

	@Test
	void updateReturns404WhenMissing() throws Exception {
		mockMvc.perform(put("/api/investments/{id}", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "Silver", "amount": 12.5}
						"""))
				.andExpect(status().isNotFound());
	}

	@Test
	void updateRejectsInvalidBody() throws Exception {
		Investment saved = investmentRepository.save(new Investment("Gold", new BigDecimal("5")));

		mockMvc.perform(put("/api/investments/{id}", saved.getId()).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "Silver", "amount": 0}
						"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	void deleteRemovesInvestment() throws Exception {
		Investment saved = investmentRepository.save(new Investment("Gold", new BigDecimal("5")));

		mockMvc.perform(delete("/api/investments/{id}", saved.getId())).andExpect(status().isNoContent());

		mockMvc.perform(get("/api/investments/{id}", saved.getId())).andExpect(status().isNotFound());
	}

	@Test
	void deleteReturns404WhenMissing() throws Exception {
		mockMvc.perform(delete("/api/investments/{id}", UUID.randomUUID())).andExpect(status().isNotFound());
	}

}
