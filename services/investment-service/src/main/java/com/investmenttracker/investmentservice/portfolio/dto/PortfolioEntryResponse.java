package com.investmenttracker.investmentservice.portfolio.dto;

import java.math.BigDecimal;

/**
 * What the user currently holds of one investment: its initial amount plus the sum of all its
 * transaction changes. Computed on every request, never stored.
 */
public record PortfolioEntryResponse(String name, String investmentType, BigDecimal amount, BigDecimal worth) {

}
