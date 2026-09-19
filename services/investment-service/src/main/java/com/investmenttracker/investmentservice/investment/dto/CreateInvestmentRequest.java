package com.investmenttracker.investmentservice.investment.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreateInvestmentRequest(
		@NotBlank @Size(max = 255) String name,
		// integer/fraction limits mirror the NUMERIC(38,18) database column
		@NotNull @Positive @Digits(integer = 20, fraction = 18) BigDecimal amount) {

}
