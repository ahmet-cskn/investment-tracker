package com.investmenttracker.investmentservice.investment.dto;

import com.investmenttracker.investmentservice.investment.Investment;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "An initial holding. Its current worth is not here: see GET /api/portfolio, "
		+ "which values this amount plus any transactions at the latest price.")
public record InvestmentResponse(UUID id, String name, BigDecimal amount,
		@Schema(description = "Derived from the catalog entry for name; null only for a row from before this column existed")
		String investmentType) {

	public static InvestmentResponse from(Investment investment) {
		return new InvestmentResponse(investment.getId(), investment.getName(), investment.getAmount(),
				investment.getInvestmentType());
	}

}
