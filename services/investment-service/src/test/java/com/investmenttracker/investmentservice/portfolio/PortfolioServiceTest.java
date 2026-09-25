package com.investmenttracker.investmentservice.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.when;

import com.investmenttracker.investmentservice.catalog.AssetType;
import com.investmenttracker.investmentservice.catalog.InvestmentCatalogEntry;
import com.investmenttracker.investmentservice.catalog.InvestmentCatalogRepository;
import com.investmenttracker.investmentservice.portfolio.dto.PortfolioEntryResponse;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PortfolioServiceTest {

	@Mock
	private PortfolioRepository portfolioRepository;

	@Mock
	private InvestmentCatalogRepository investmentCatalogRepository;

	@InjectMocks
	private PortfolioService portfolioService;

	@BeforeEach
	void stubCatalog() {
		org.mockito.Mockito.lenient()
				.when(investmentCatalogRepository.findAllById(anyIterable()))
				.thenReturn(List.of(new InvestmentCatalogEntry("Gold", "Precious Metal", AssetType.METAL, "GOLD"),
						new InvestmentCatalogEntry("Bitcoin", "Cryptocurrency", AssetType.CRYPTO, "BTC")));
	}

	private static NameTotal total(String name, String value) {
		return new NameTotal(name, new BigDecimal(value));
	}

	@Test
	void addsTheInitialAmountAndTheTransactionChanges() {
		// The example from the spec: initial 2, then transactions of -1 and 5 (already summed to 4 by the database)
		when(portfolioRepository.sumInitialAmountsByName()).thenReturn(List.of(total("Gold", "2")));
		when(portfolioRepository.sumChangesByName()).thenReturn(List.of(total("Gold", "4")));

		List<PortfolioEntryResponse> portfolio = portfolioService.getPortfolio();

		assertThat(portfolio).hasSize(1);
		assertThat(portfolio.get(0).name()).isEqualTo("Gold");
		assertThat(portfolio.get(0).amount()).isEqualByComparingTo("6");
	}

	@Test
	void includesANameThatOnlyHasTransactions() {
		when(portfolioRepository.sumInitialAmountsByName()).thenReturn(List.of());
		when(portfolioRepository.sumChangesByName()).thenReturn(List.of(total("Bitcoin", "1.5")));

		List<PortfolioEntryResponse> portfolio = portfolioService.getPortfolio();

		assertThat(portfolio).singleElement().satisfies(entry -> {
			assertThat(entry.name()).isEqualTo("Bitcoin");
			assertThat(entry.amount()).isEqualByComparingTo("1.5");
		});
	}

	@Test
	void includesANameThatOnlyHasAnInitialAmount() {
		when(portfolioRepository.sumInitialAmountsByName()).thenReturn(List.of(total("Gold", "3")));
		when(portfolioRepository.sumChangesByName()).thenReturn(List.of());

		assertThat(portfolioService.getPortfolio()).singleElement()
				.satisfies(entry -> assertThat(entry.amount()).isEqualByComparingTo("3"));
	}

	@Test
	void keepsATotalOfZeroAndANegativeTotal() {
		when(portfolioRepository.sumInitialAmountsByName())
				.thenReturn(List.of(total("Gold", "2"), total("Bitcoin", "1")));
		when(portfolioRepository.sumChangesByName()).thenReturn(List.of(total("Gold", "-2"), total("Bitcoin", "-3")));

		List<PortfolioEntryResponse> portfolio = portfolioService.getPortfolio();

		assertThat(portfolio).extracting(PortfolioEntryResponse::name).containsExactly("Bitcoin", "Gold");
		assertThat(portfolio.get(0).amount()).isEqualByComparingTo("-2");
		assertThat(portfolio.get(1).amount()).isEqualByComparingTo("0");
	}

	@Test
	void sumsWithoutLosingPrecision() {
		when(portfolioRepository.sumInitialAmountsByName())
				.thenReturn(List.of(total("Gold", "0.000000000000000001")));
		when(portfolioRepository.sumChangesByName()).thenReturn(List.of(total("Gold", "0.000000000000000002")));

		assertThat(portfolioService.getPortfolio()).singleElement()
				.satisfies(entry -> assertThat(entry.amount()).isEqualByComparingTo("0.000000000000000003"));
	}

	@Test
	void takesTheTypeFromTheCatalogAndUsesThePlaceholderWorth() {
		when(portfolioRepository.sumInitialAmountsByName()).thenReturn(List.of(total("Gold", "2")));
		when(portfolioRepository.sumChangesByName()).thenReturn(List.of());

		assertThat(portfolioService.getPortfolio()).singleElement().satisfies(entry -> {
			assertThat(entry.investmentType()).isEqualTo("Precious Metal");
			assertThat(entry.worth()).isEqualByComparingTo("1");
		});
	}

	@Test
	void givesANameOutsideTheCatalogNoType() {
		when(portfolioRepository.sumInitialAmountsByName()).thenReturn(List.of(total("Unlisted", "2")));
		when(portfolioRepository.sumChangesByName()).thenReturn(List.of());

		assertThat(portfolioService.getPortfolio()).singleElement()
				.satisfies(entry -> assertThat(entry.investmentType()).isNull());
	}

	@Test
	void returnsAnEmptyListWhenThereIsNothing() {
		when(portfolioRepository.sumInitialAmountsByName()).thenReturn(List.of());
		when(portfolioRepository.sumChangesByName()).thenReturn(List.of());

		assertThat(portfolioService.getPortfolio()).isEmpty();
	}

}
