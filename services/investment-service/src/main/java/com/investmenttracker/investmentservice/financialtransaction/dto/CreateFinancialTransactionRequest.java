package com.investmenttracker.investmentservice.financialtransaction.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateFinancialTransactionRequest(
		// free text, so its length has to be checked against the varchar(255) column
		@Schema(description = "Free text describing the transaction; not related to an investment", example = "Groceries")
		@NotBlank @Size(max = 255) String name,
		// integer/fraction limits mirror the NUMERIC(38,18) database column; signed, negative for spending
		@Schema(description = "In USD; positive for income, negative for spending", example = "-42.5")
		@NotNull @Digits(integer = 20, fraction = 18) BigDecimal change,
		@Schema(example = "2026-01-15") @NotNull LocalDate date) {

}
