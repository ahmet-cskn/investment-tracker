package com.investmenttracker.investmentservice.portfolio;

import java.math.BigDecimal;

/** An investment name with the sum of some amounts recorded under it. */
public record NameTotal(String name, BigDecimal total) {

}
