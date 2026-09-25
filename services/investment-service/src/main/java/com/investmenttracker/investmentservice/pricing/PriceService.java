package com.investmenttracker.investmentservice.pricing;

import com.investmenttracker.investmentservice.catalog.InvestmentCatalogEntry;
import com.investmenttracker.investmentservice.catalog.InvestmentCatalogRepository;
import com.investmenttracker.investmentservice.catalog.UnknownInvestmentNameException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Answers "what did this investment cost per unit on this day", from a cache in the database.
 *
 * <p>The provider allows only 25 calls a day, and one call returns an asset's whole history, so it is
 * called only to fill an empty cache, or to extend it when a day newer than anything cached is asked for.
 * Even then an investment is refreshed at most once a day, so a weekend, when nothing newer than Friday
 * exists, does not cause a call per request. Past prices do not change, so everything else is a read.
 *
 * <p>When the provider cannot help, the answer is simply empty; callers decide what that means.
 */
@Service
public class PriceService {

	private static final Logger log = LoggerFactory.getLogger(PriceService.class);

	// A refresh rewrites the last few days too, since the newest close may have been a partial day when cached
	private static final int REFRESH_OVERLAP_DAYS = 7;

	private final PriceCacheRepository priceCache;
	private final PriceProvider priceProvider;
	private final InvestmentCatalogRepository investmentCatalogRepository;
	private final Clock clock;

	public PriceService(PriceCacheRepository priceCache, PriceProvider priceProvider,
			InvestmentCatalogRepository investmentCatalogRepository, Clock clock) {
		this.priceCache = priceCache;
		this.priceProvider = priceProvider;
		this.investmentCatalogRepository = investmentCatalogRepository;
		this.clock = clock;
	}

	/**
	 * The latest price on or before the day, in USD per unit of the investment's amount (per gram for the
	 * metals). A weekend or holiday therefore gets the last trading day's price, and a day in the future
	 * gets the newest price there is. Empty if there is no price that early, or none can be had.
	 *
	 * @throws UnknownInvestmentNameException if the name is not in the catalog
	 */
	public Optional<BigDecimal> findPrice(String name, LocalDate date) {
		InvestmentCatalogEntry entry = investmentCatalogRepository.findById(name)
				.orElseThrow(() -> new UnknownInvestmentNameException(name));

		if (needsRefresh(name, date)) {
			refresh(entry);
		}
		return priceCache.findLatestOnOrBefore(name, date);
	}

	private boolean needsRefresh(String name, LocalDate date) {
		LocalDate today = LocalDate.now(clock);
		boolean syncedToday = priceCache.findSyncedOn(name).filter(day -> !day.isBefore(today)).isPresent();
		if (syncedToday) {
			return false;
		}
		// Nothing cached yet, or the day asked for is newer than the newest cached one and may have appeared since
		return priceCache.findNewestDate(name).map(newest -> date.isAfter(newest)).orElse(true);
	}

	private void refresh(InvestmentCatalogEntry entry) {
		String name = entry.getName();
		try {
			Map<LocalDate, BigDecimal> fetched = priceProvider.fetchDailyPrices(entry.getAssetType(),
					entry.getPriceSymbol());

			// Only the recent part of a big history needs writing once there is a cache
			LocalDate cutoff = priceCache.findNewestDate(name).map(newest -> newest.minusDays(REFRESH_OVERLAP_DAYS))
					.orElse(LocalDate.MIN);
			Map<LocalDate, BigDecimal> toStore = fetched.entrySet()
					.stream()
					.filter(price -> !price.getKey().isBefore(cutoff))
					.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

			priceCache.upsertAll(name, toStore);
			priceCache.markSynced(name, LocalDate.now(clock));
		}
		catch (PriceUnavailableException e) {
			// Not marked as synced, so the next request tries again
			log.warn("Could not refresh the prices of {}: {}", name, e.getMessage());
		}
	}

}
