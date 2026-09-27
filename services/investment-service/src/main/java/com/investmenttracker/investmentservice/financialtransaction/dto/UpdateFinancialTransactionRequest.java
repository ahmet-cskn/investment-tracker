package com.investmenttracker.investmentservice.financialtransaction.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateFinancialTransactionRequest(
		// free text, so its length has to be checked against the varchar(255) column
		@NotBlank @Size(max = 255) String name,
		// integer/fraction limits mirror the NUMERIC(38,18) database column; signed, negative for spending
		@NotNull @Digits(integer = 20, fraction = 18) BigDecimal change,
		@NotNull LocalDate date) {

}
