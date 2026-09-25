package com.investmenttracker.investmentservice.portfolio.dto;

import java.math.BigDecimal;

/**
 * What the user currently holds of one investment: its initial amount plus the sum of all its
 * transaction changes, and what that is worth in USD at the latest price (null if no price could be obtained).
 * Computed on every request, never stored.
 */
public record PortfolioEntryResponse(String name, String investmentType, BigDecimal amount, BigDecimal worth) {

}
