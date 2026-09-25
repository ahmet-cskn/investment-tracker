package com.investmenttracker.investmentservice.transactionhistory.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateTransactionRequest(
		@NotBlank String name,
		@NotNull @Digits(integer = 20, fraction = 18) BigDecimal change,
		@NotNull LocalDate date) {

}
