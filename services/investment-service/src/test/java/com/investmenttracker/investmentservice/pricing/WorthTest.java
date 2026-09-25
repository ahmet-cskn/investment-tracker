package com.investmenttracker.investmentservice.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class WorthTest {

	private static Optional<BigDecimal> price(String value) {
		return Optional.of(new BigDecimal(value));
	}

	@Test
	void isThePriceTimesTheAmount() {
		assertThat(Worth.of(price("171.5"), new BigDecimal("2"))).hasValueSatisfying(
				worth -> assertThat(worth).isEqualByComparingTo("343"));
	}

	@Test
	void isNegativeForANegativeAmount() {
		assertThat(Worth.of(price("50000"), new BigDecimal("-1.5"))).hasValueSatisfying(
				worth -> assertThat(worth).isEqualByComparingTo("-75000"));
	}

	@Test
	void isEmptyWithoutAPrice() {
		assertThat(Worth.of(Optional.empty(), BigDecimal.ONE)).isEmpty();
	}

	@Test
	void roundsHalfToEvenAtTheEighteenthDecimal() {
		assertThat(Worth.of(price("0.5"), new BigDecimal("0.000000000000000003"))).hasValueSatisfying(
				worth -> assertThat(worth).isEqualByComparingTo("0.000000000000000002"));
		assertThat(Worth.of(price("0.5"), new BigDecimal("0.000000000000000005"))).hasValueSatisfying(
				worth -> assertThat(worth).isEqualByComparingTo("0.000000000000000002"));
	}

	@Test
	void fitsTwentyIntegerDigitsButNotTwentyOne() {
		assertThat(Worth.of(price("1"), new BigDecimal("99999999999999999999"))).isPresent();
		assertThat(Worth.of(price("1"), new BigDecimal("100000000000000000000"))).isEmpty();
	}

}
