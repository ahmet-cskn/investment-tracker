package com.investmenttracker.investmentservice.transactionhistory.dto;

import com.investmenttracker.investmentservice.transactionhistory.TransactionHistory;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TransactionResponse(UUID id, String name, String investmentType, BigDecimal change, LocalDate date,
		@Schema(description = "The change's value in USD on date (that day's price times change), or null if no "
				+ "price could be obtained", nullable = true)
		BigDecimal worth) {

	public static TransactionResponse from(TransactionHistory entry) {
		return new TransactionResponse(entry.getId(), entry.getName(), entry.getInvestmentType(), entry.getChange(),
				entry.getDate(), entry.getWorth());
	}

}
