package com.investmenttracker.investmentservice.investment;

/** Thrown when a create/update request names an investment that is not in the catalog. */
public class UnknownInvestmentNameException extends RuntimeException {

	public UnknownInvestmentNameException(String name) {
		super("Unknown investment name: " + name);
	}

}
