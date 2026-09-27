package com.investmenttracker.investmentservice.portfolio.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "What the user currently holds of one investment: its initial amount plus the sum of all "
		+ "its transaction changes, and what that is worth in USD at the latest price. Computed on every request, "
		+ "never stored.")
public record PortfolioEntryResponse(String name, String investmentType, BigDecimal amount,
		@Schema(description = "amount times the latest price, or null when no price could be obtained", nullable = true)
		BigDecimal worth) {

}
