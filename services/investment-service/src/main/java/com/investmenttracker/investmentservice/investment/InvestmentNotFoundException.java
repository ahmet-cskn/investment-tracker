package com.investmenttracker.investmentservice.investment;

import java.util.UUID;

public class InvestmentNotFoundException extends RuntimeException {

	public InvestmentNotFoundException(UUID id) {
		super("Investment with id " + id + " not found");
	}

}
