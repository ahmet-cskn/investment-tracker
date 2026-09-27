package com.investmenttracker.investmentservice.transactionhistory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

// investmentType is intentionally absent: like on investments, it is derived from name via the catalog
public record CreateTransactionRequest(
		@Schema(description = "Must be one of the names listed by GET /api/catalog", example = "Gold")
		@NotBlank String name,
		// integer/fraction limits mirror the NUMERIC(38,18) database column; unlike amount, this is signed
		@Schema(description = "In the investment's own unit; negative for a decrease, zero allowed", example = "2.5")
		@NotNull @Digits(integer = 20, fraction = 18) BigDecimal change,
		@Schema(description = "The day the change happened; used to look up that day's price", example = "2026-01-15")
		@NotNull LocalDate date) {

}
