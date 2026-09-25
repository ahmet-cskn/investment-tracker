package com.investmenttracker.investmentservice.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.investmenttracker.investmentservice.catalog.AssetType;
import com.investmenttracker.investmentservice.catalog.InvestmentCatalogEntry;
import com.investmenttracker.investmentservice.catalog.InvestmentCatalogRepository;
import com.investmenttracker.investmentservice.portfolio.dto.PortfolioEntryResponse;
import com.investmenttracker.investmentservice.pricing.PriceService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PortfolioServiceTest {

	@Mock
	private PortfolioRepository portfolioRepository;

	@Mock
	private InvestmentCatalogRepository investmentCatalogRepository;

	@Mock
	private PriceService priceService;

	private static final LocalDate TODAY = LocalDate.parse("2026-09-25");

	private PortfolioService portfolioService;

	@BeforeEach
	void stubCatalog() {
		Clock clock = Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
		portfolioService = new PortfolioService(portfolioRepository, investmentCatalogRepository, priceService, clock);
		// Unless a test says otherwise, there is no price for anything
		lenient().when(priceService.findPrice(anyString(), any(LocalDate.class))).thenReturn(Optional.empty());
		lenient()
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
	void takesTheTypeFromTheCatalog() {
		when(portfolioRepository.sumInitialAmountsByName()).thenReturn(List.of(total("Gold", "2")));
		when(portfolioRepository.sumChangesByName()).thenReturn(List.of());

		assertThat(portfolioService.getPortfolio()).singleElement()
				.satisfies(entry -> assertThat(entry.investmentType()).isEqualTo("Precious Metal"));
	}

	@Test
	void valuesTheTotalAtTheLatestPrice() {
		// 2 + 4 = 6 grams at 171.5 each, looked up as of today
		when(portfolioRepository.sumInitialAmountsByName()).thenReturn(List.of(total("Gold", "2")));
		when(portfolioRepository.sumChangesByName()).thenReturn(List.of(total("Gold", "4")));
		when(priceService.findPrice("Gold", TODAY)).thenReturn(Optional.of(new BigDecimal("171.5")));

		assertThat(portfolioService.getPortfolio()).singleElement()
				.satisfies(entry -> assertThat(entry.worth()).isEqualByComparingTo("1029"));
	}

	@Test
	void valuesANegativeTotalNegatively() {
		when(portfolioRepository.sumInitialAmountsByName()).thenReturn(List.of());
		when(portfolioRepository.sumChangesByName()).thenReturn(List.of(total("Bitcoin", "-2")));
		when(priceService.findPrice("Bitcoin", TODAY)).thenReturn(Optional.of(new BigDecimal("50000")));

		assertThat(portfolioService.getPortfolio()).singleElement()
				.satisfies(entry -> assertThat(entry.worth()).isEqualByComparingTo("-100000"));
	}

	@Test
	void keepsTheWorthExactAtEighteenDecimals() {
		when(portfolioRepository.sumInitialAmountsByName()).thenReturn(List.of(total("Bitcoin", "0.000000000000000003")));
		when(portfolioRepository.sumChangesByName()).thenReturn(List.of());
		when(priceService.findPrice("Bitcoin", TODAY)).thenReturn(Optional.of(new BigDecimal("0.5")));

		// 1.5E-18 rounds half to even at the 18th decimal
		assertThat(portfolioService.getPortfolio()).singleElement()
				.satisfies(entry -> assertThat(entry.worth()).isEqualByComparingTo("0.000000000000000002"));
	}

	@Test
	void hasNoWorthWhenNoPriceCouldBeObtained() {
		when(portfolioRepository.sumInitialAmountsByName()).thenReturn(List.of(total("Gold", "2"), total("Bitcoin", "1")));
		when(portfolioRepository.sumChangesByName()).thenReturn(List.of());
		when(priceService.findPrice("Bitcoin", TODAY)).thenReturn(Optional.of(new BigDecimal("50000")));

		List<PortfolioEntryResponse> portfolio = portfolioService.getPortfolio();

		// Gold has no price, but that does not stop Bitcoin from being valued
		assertThat(portfolio).extracting(PortfolioEntryResponse::name).containsExactly("Bitcoin", "Gold");
		assertThat(portfolio.get(0).worth()).isEqualByComparingTo("50000");
		assertThat(portfolio.get(1).worth()).isNull();
	}

	@Test
	void hasNoWorthWhenItWouldNotFitTheNumericRange() {
		when(portfolioRepository.sumInitialAmountsByName()).thenReturn(List.of(total("Gold", "99999999999999999999")));
		when(portfolioRepository.sumChangesByName()).thenReturn(List.of());
		when(priceService.findPrice("Gold", TODAY)).thenReturn(Optional.of(new BigDecimal("100")));

		assertThat(portfolioService.getPortfolio()).singleElement()
				.satisfies(entry -> assertThat(entry.worth()).isNull());
	}

	@Test
	void givesANameOutsideTheCatalogNoType() {
		when(portfolioRepository.sumInitialAmountsByName()).thenReturn(List.of(total("Unlisted", "2")));
		when(portfolioRepository.sumChangesByName()).thenReturn(List.of());

		assertThat(portfolioService.getPortfolio()).singleElement().satisfies(entry -> {
			assertThat(entry.investmentType()).isNull();
			assertThat(entry.worth()).isNull();
		});
		// the price service rejects names outside the catalog, so it is not even asked
		verify(priceService, never()).findPrice(anyString(), any(LocalDate.class));
	}

	@Test
	void returnsAnEmptyListWhenThereIsNothing() {
		when(portfolioRepository.sumInitialAmountsByName()).thenReturn(List.of());
		when(portfolioRepository.sumChangesByName()).thenReturn(List.of());

		assertThat(portfolioService.getPortfolio()).isEmpty();
	}

}
