package com.investmenttracker.investmentservice.transactionhistory;

import java.util.UUID;

public class TransactionNotFoundException extends RuntimeException {

	public TransactionNotFoundException(UUID id) {
		super("Transaction with id " + id + " not found");
	}

}
