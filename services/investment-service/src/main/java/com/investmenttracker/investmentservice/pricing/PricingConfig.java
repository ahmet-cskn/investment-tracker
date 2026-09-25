package com.investmenttracker.investmentservice.pricing;

import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(AlphaVantageProperties.class)
public class PricingConfig {

	/** Days are counted in UTC, and tests replace this bean to control "today". */
	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

	// RestClient.builder() rather than an injected builder: Spring Boot only provides one with an extra module, and
	// nothing here needs its message-converter setup, since the provider reads the response as plain text
	@Bean
	PriceProvider priceProvider(AlphaVantageProperties properties) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(Duration.ofSeconds(5));
		// a stock's whole history is about a megabyte, so reading it gets more time than connecting
		requestFactory.setReadTimeout(Duration.ofSeconds(30));
		RestClient restClient = RestClient.builder().baseUrl(properties.baseUrl()).requestFactory(requestFactory).build();
		return new AlphaVantagePriceProvider(restClient, properties.apiKey());
	}

}
