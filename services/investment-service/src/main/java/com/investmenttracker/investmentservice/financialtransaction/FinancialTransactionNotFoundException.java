package com.investmenttracker.investmentservice.financialtransaction;

import java.util.UUID;

public class FinancialTransactionNotFoundException extends RuntimeException {

	public FinancialTransactionNotFoundException(UUID id) {
		super("Financial transaction with id " + id + " not found");
	}

}
