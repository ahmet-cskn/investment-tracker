package com.investmenttracker.investmentservice.pricing;

import com.investmenttracker.investmentservice.catalog.AssetType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/** Where daily prices come from. Kept behind an interface so the provider can be replaced, and faked in tests. */
public interface PriceProvider {

	/**
	 * Every daily closing price the provider has for a symbol, in USD per unit of the investment's amount:
	 * per gram for metals, per coin, per share. Converting the provider's own units is the provider's job.
	 *
	 * @throws PriceUnavailableException if the prices cannot be obtained
	 */
	Map<LocalDate, BigDecimal> fetchDailyPrices(AssetType assetType, String symbol);

}
