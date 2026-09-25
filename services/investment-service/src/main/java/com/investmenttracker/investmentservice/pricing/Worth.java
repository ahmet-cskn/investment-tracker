package com.investmenttracker.investmentservice.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Works out what an amount of an investment is worth in USD, in the shape the worth columns can hold. */
public final class Worth {

	private static final Logger log = LoggerFactory.getLogger(Worth.class);

	// The worth columns are NUMERIC(38,18): 18 decimals and so at most 20 digits before the point
	private static final int SCALE = 18;
	private static final int MAX_INTEGER_DIGITS = 20;

	private Worth() {
	}

	/**
	 * The price times the amount, or empty if there is no price or the result would not fit a worth column.
	 * A negative amount (a sale) gives a negative worth.
	 */
	public static Optional<BigDecimal> of(Optional<BigDecimal> price, BigDecimal amount) {
		if (price.isEmpty()) {
			return Optional.empty();
		}
		BigDecimal worth = price.get().multiply(amount).setScale(SCALE, RoundingMode.HALF_EVEN);
		if (worth.precision() - worth.scale() > MAX_INTEGER_DIGITS) {
			log.warn("A worth of {} at {} each is too large to store", amount, price.get());
			return Optional.empty();
		}
		return Optional.of(worth);
	}

}
