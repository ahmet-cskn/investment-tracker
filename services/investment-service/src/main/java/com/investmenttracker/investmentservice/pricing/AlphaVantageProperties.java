package com.investmenttracker.investmentservice.pricing;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param baseUrl the API host
 * @param apiKey the free key from alphavantage.co; may be blank, in which case prices are simply unavailable
 */
@ConfigurationProperties(prefix = "pricing.alphavantage")
public record AlphaVantageProperties(String baseUrl, String apiKey) {

}
