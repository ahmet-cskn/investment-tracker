package com.investmenttracker.investmentservice.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Powers the generated OpenAPI document and the Swagger UI at /swagger-ui.html. */
@Configuration
public class OpenApiConfig {

	@Bean
	OpenAPI investmentTrackerOpenApi() {
		return new OpenAPI().info(new Info().title("Investment Tracker API")
				.description("""
						Tracks investments (precious metals, cryptocurrency and a stock) and everyday income and spending. \
						Investment worths are priced from Alpha Vantage and cached; amounts are exact decimals, never \
						floating point, to avoid losing precision.""")
				.version("v1")
				.license(new License().name("Unlicensed - personal project")));
	}

}
