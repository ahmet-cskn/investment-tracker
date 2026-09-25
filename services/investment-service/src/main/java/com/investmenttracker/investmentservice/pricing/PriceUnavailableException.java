package com.investmenttracker.investmentservice.pricing;

/**
 * A price could not be obtained from the provider: no API key, the daily quota is used up, the provider
 * is unreachable, or it answered with something unexpected. Messages must never contain the API key.
 */
public class PriceUnavailableException extends RuntimeException {

	public PriceUnavailableException(String message) {
		super(message);
	}

}
