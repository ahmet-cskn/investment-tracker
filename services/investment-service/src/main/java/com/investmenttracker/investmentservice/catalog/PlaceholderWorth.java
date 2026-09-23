package com.investmenttracker.investmentservice.catalog;

import java.math.BigDecimal;

/**
 * worth is not yet priced by a real market-data source; every investment is placeholder-valued at 1
 * until that is built. Kept in one place so investments and the portfolio view always agree.
 */
public final class PlaceholderWorth {

	public static final BigDecimal VALUE = BigDecimal.ONE;

	private PlaceholderWorth() {
	}

}
