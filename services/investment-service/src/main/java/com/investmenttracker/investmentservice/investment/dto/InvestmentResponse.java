package com.investmenttracker.investmentservice.investment.dto;

import com.investmenttracker.investmentservice.investment.Investment;
import java.math.BigDecimal;
import java.util.UUID;

public record InvestmentResponse(UUID id, String name, BigDecimal amount, String investmentType) {

	public static InvestmentResponse from(Investment investment) {
		return new InvestmentResponse(investment.getId(), investment.getName(), investment.getAmount(),
				investment.getInvestmentType());
	}

}
