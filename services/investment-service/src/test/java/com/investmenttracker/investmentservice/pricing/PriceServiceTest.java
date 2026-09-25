package com.investmenttracker.investmentservice.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.investmenttracker.investmentservice.catalog.AssetType;
import com.investmenttracker.investmentservice.catalog.InvestmentCatalogEntry;
import com.investmenttracker.investmentservice.catalog.InvestmentCatalogRepository;
import com.investmenttracker.investmentservice.catalog.UnknownInvestmentNameException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PriceServiceTest {

	// A Monday. The Friday before it is 2026-09-18.
	private static final LocalDate TODAY = LocalDate.parse("2026-09-21");
	private static final LocalDate FRIDAY = LocalDate.parse("2026-09-18");

	@Mock
	private PriceCacheRepository priceCache;

	@Mock
	private PriceProvider priceProvider;

	@Mock
	private InvestmentCatalogRepository investmentCatalogRepository;

	@Captor
	private ArgumentCaptor<Map<LocalDate, BigDecimal>> storedPrices;

	private PriceService priceService;

	@BeforeEach
	void setUp() {
		Clock clock = Clock.fixed(Instant.parse(TODAY + "T10:00:00Z"), ZoneOffset.UTC);
		priceService = new PriceService(priceCache, priceProvider, investmentCatalogRepository, clock);
		org.mockito.Mockito.lenient()
				.when(investmentCatalogRepository.findById("Gold"))
				.thenReturn(Optional.of(new InvestmentCatalogEntry("Gold", "Precious Metal", AssetType.METAL, "GOLD")));
	}

	private void cacheHolds(LocalDate newest, LocalDate syncedOn) {
		when(priceCache.findSyncedOn("Gold")).thenReturn(Optional.ofNullable(syncedOn));
		org.mockito.Mockito.lenient().when(priceCache.findNewestDate("Gold")).thenReturn(Optional.ofNullable(newest));
	}

	private static Map<LocalDate, BigDecimal> provided(String... dateAndPrice) {
		Map<LocalDate, BigDecimal> prices = new java.util.HashMap<>();
		for (int i = 0; i < dateAndPrice.length; i += 2) {
			prices.put(LocalDate.parse(dateAndPrice[i]), new BigDecimal(dateAndPrice[i + 1]));
		}
		return prices;
	}

	@Test
	void anEmptyCacheIsFilledFromTheProviderOnFirstUse() {
		cacheHolds(null, null);
		when(priceProvider.fetchDailyPrices(AssetType.METAL, "GOLD"))
				.thenReturn(provided("2020-01-02", "50", "2026-09-18", "137"));
		when(priceCache.findLatestOnOrBefore("Gold", FRIDAY)).thenReturn(Optional.of(new BigDecimal("137")));

		Optional<BigDecimal> price = priceService.findPrice("Gold", FRIDAY);

		assertThat(price).hasValue(new BigDecimal("137"));
		verify(priceCache).upsertAll(eq("Gold"), storedPrices.capture());
		assertThat(storedPrices.getValue()).hasSize(2); // the whole history, when nothing is cached
		verify(priceCache).markSynced("Gold", TODAY);
	}

	@Test
	void theProviderReceivesTheAssetTypeAndSymbolFromTheCatalog() {
		cacheHolds(null, null);
		when(priceProvider.fetchDailyPrices(AssetType.METAL, "GOLD")).thenReturn(provided());
		when(priceCache.findLatestOnOrBefore(anyString(), any())).thenReturn(Optional.empty());

		priceService.findPrice("Gold", FRIDAY);

		verify(priceProvider).fetchDailyPrices(AssetType.METAL, "GOLD");
	}

	@Test
	void aPastDayThatIsAlreadyCachedNeverCallsTheProvider() {
		cacheHolds(FRIDAY, FRIDAY.minusDays(30));
		when(priceCache.findLatestOnOrBefore("Gold", LocalDate.parse("2026-01-15"))).thenReturn(Optional.of(new BigDecimal("120")));

		Optional<BigDecimal> price = priceService.findPrice("Gold", LocalDate.parse("2026-01-15"));

		assertThat(price).hasValue(new BigDecimal("120"));
		verify(priceProvider, never()).fetchDailyPrices(any(), anyString());
	}

	@Test
	void aDayNewerThanTheCacheTriggersOneRefreshAndOnlyTheRecentPartIsRewritten() {
		cacheHolds(FRIDAY, FRIDAY); // last refreshed on Friday, so not today
		when(priceProvider.fetchDailyPrices(AssetType.METAL, "GOLD"))
				.thenReturn(provided("2026-09-01", "130", "2026-09-10", "133", "2026-09-18", "137", "2026-09-21", "138"));
		when(priceCache.findLatestOnOrBefore("Gold", TODAY)).thenReturn(Optional.of(new BigDecimal("138")));

		Optional<BigDecimal> price = priceService.findPrice("Gold", TODAY);

		assertThat(price).hasValue(new BigDecimal("138"));
		verify(priceCache).upsertAll(eq("Gold"), storedPrices.capture());
		// newest cached is 09-18, so the overlap starts on 09-11: the September 1st and 10th prices are left alone
		assertThat(storedPrices.getValue()).containsOnlyKeys(FRIDAY, TODAY);
		verify(priceCache).markSynced("Gold", TODAY);
	}

	@Test
	void aSecondLookupTheSameDayDoesNotCallTheProviderEvenIfNothingNewerExists() {
		// e.g. a weekend: the newest price is still Friday's, but today's refresh already happened
		cacheHolds(FRIDAY, TODAY);
		when(priceCache.findLatestOnOrBefore("Gold", TODAY)).thenReturn(Optional.of(new BigDecimal("137")));

		Optional<BigDecimal> price = priceService.findPrice("Gold", TODAY);

		assertThat(price).hasValue(new BigDecimal("137"));
		verify(priceProvider, never()).fetchDailyPrices(any(), anyString());
	}

	@Test
	void theNextDayRefreshesAgain() {
		cacheHolds(FRIDAY, TODAY.minusDays(1));
		when(priceProvider.fetchDailyPrices(AssetType.METAL, "GOLD")).thenReturn(provided("2026-09-21", "138"));
		when(priceCache.findLatestOnOrBefore("Gold", TODAY)).thenReturn(Optional.of(new BigDecimal("138")));

		priceService.findPrice("Gold", TODAY);

		verify(priceProvider).fetchDailyPrices(AssetType.METAL, "GOLD");
	}

	@Test
	void aFutureDayGetsTheNewestPriceThereIs() {
		cacheHolds(FRIDAY, TODAY);
		LocalDate future = TODAY.plusDays(90);
		when(priceCache.findLatestOnOrBefore("Gold", future)).thenReturn(Optional.of(new BigDecimal("137")));

		assertThat(priceService.findPrice("Gold", future)).hasValue(new BigDecimal("137"));
	}

	@Test
	void aDayBeforeAnyPriceExistsHasNoPrice() {
		cacheHolds(FRIDAY, TODAY);
		when(priceCache.findLatestOnOrBefore("Gold", LocalDate.parse("1990-01-01"))).thenReturn(Optional.empty());

		assertThat(priceService.findPrice("Gold", LocalDate.parse("1990-01-01"))).isEmpty();
		verify(priceProvider, never()).fetchDailyPrices(any(), anyString());
	}

	@Test
	void whenTheProviderFailsTheAnswerComesFromTheCacheAndTheNextLookupTriesAgain() {
		cacheHolds(FRIDAY, FRIDAY);
		when(priceProvider.fetchDailyPrices(AssetType.METAL, "GOLD"))
				.thenThrow(new PriceUnavailableException("Alpha Vantage: rate limit"));
		when(priceCache.findLatestOnOrBefore("Gold", TODAY)).thenReturn(Optional.of(new BigDecimal("137")));

		Optional<BigDecimal> price = priceService.findPrice("Gold", TODAY);

		// no exception: the newest cached price is what there is
		assertThat(price).hasValue(new BigDecimal("137"));
		verify(priceCache, never()).markSynced(anyString(), any());
		verify(priceCache, never()).upsertAll(anyString(), any());
	}

	@Test
	void whenTheProviderFailsOnAnEmptyCacheTheAnswerIsEmpty() {
		cacheHolds(null, null);
		when(priceProvider.fetchDailyPrices(AssetType.METAL, "GOLD"))
				.thenThrow(new PriceUnavailableException("No Alpha Vantage API key is configured"));
		when(priceCache.findLatestOnOrBefore("Gold", FRIDAY)).thenReturn(Optional.empty());

		assertThat(priceService.findPrice("Gold", FRIDAY)).isEmpty();
	}

	@Test
	void anUnknownNameIsRejected() {
		when(investmentCatalogRepository.findById("Doge")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> priceService.findPrice("Doge", FRIDAY))
				.isInstanceOf(UnknownInvestmentNameException.class)
				.hasMessageContaining("Doge");
		verify(priceProvider, never()).fetchDailyPrices(any(), anyString());
	}

}
