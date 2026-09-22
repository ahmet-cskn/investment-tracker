package com.investmenttracker.investmentservice.catalog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Checks the catalog seeded by the Liquibase changelog, not just the endpoint's plumbing. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CatalogControllerIT {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

	@Autowired
	private MockMvc mockMvc;

	@Test
	void returnsTheSeededCatalogSortedByName() throws Exception {
		mockMvc.perform(get("/api/catalog"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(5)))
				.andExpect(jsonPath("$[0].name").value("Bitcoin"))
				.andExpect(jsonPath("$[0].investmentType").value("Cryptocurrency"))
				.andExpect(jsonPath("$[1].name").value("Ethereum"))
				.andExpect(jsonPath("$[2].name").value("Gold"))
				.andExpect(jsonPath("$[2].investmentType").value("Precious Metal"))
				.andExpect(jsonPath("$[3].name").value("S&P500"))
				.andExpect(jsonPath("$[3].investmentType").value("Stock"))
				.andExpect(jsonPath("$[4].name").value("Silver"));
	}

}
