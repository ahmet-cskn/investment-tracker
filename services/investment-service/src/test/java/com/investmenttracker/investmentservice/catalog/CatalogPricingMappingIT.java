package com.investmenttracker.investmentservice.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** The catalog seeded by the Liquibase changelog says how to price each investment. */
@SpringBootTest
@Testcontainers
class CatalogPricingMappingIT {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

	@Autowired
	private InvestmentCatalogRepository investmentCatalogRepository;

	@Test
	void everyInvestmentHasAnAssetTypeAndAProviderSymbol() {
		List<InvestmentCatalogEntry> catalog = investmentCatalogRepository.findAll();

		assertThat(catalog)
				.extracting(InvestmentCatalogEntry::getName, InvestmentCatalogEntry::getAssetType,
						InvestmentCatalogEntry::getPriceSymbol)
				.containsExactlyInAnyOrder(
						org.assertj.core.groups.Tuple.tuple("Gold", AssetType.METAL, "GOLD"),
						org.assertj.core.groups.Tuple.tuple("Silver", AssetType.METAL, "SILVER"),
						org.assertj.core.groups.Tuple.tuple("Bitcoin", AssetType.CRYPTO, "BTC"),
						org.assertj.core.groups.Tuple.tuple("Ethereum", AssetType.CRYPTO, "ETH"),
						// an index's history is not in the free tier, so it is tracked through an ETF
						org.assertj.core.groups.Tuple.tuple("S&P500", AssetType.STOCK, "SPY"));
	}

}
