package com.investmenttracker.investmentservice.transactionhistory.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;

// investmentType is intentionally absent: like on investments, it is derived from name via the catalog
public record CreateTransactionRequest(
		@NotBlank String name,
		// integer/fraction limits mirror the NUMERIC(38,18) database column; unlike amount, this is signed
		@NotNull @Digits(integer = 20, fraction = 18) BigDecimal change,
		@NotNull Instant timestamp) {

}
