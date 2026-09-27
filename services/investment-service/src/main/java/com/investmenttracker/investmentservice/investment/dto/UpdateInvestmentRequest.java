package com.investmenttracker.investmentservice.investment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record UpdateInvestmentRequest(
		@Schema(description = "Must be one of the names listed by GET /api/catalog", example = "Gold")
		@NotBlank @Size(max = 255) String name,
		// integer/fraction limits mirror the NUMERIC(38,18) database column
		@Schema(description = "How much is held, in the investment's own unit (e.g. grams for a metal)", example = "2.5")
		@NotNull @Positive @Digits(integer = 20, fraction = 18) BigDecimal amount) {

}
