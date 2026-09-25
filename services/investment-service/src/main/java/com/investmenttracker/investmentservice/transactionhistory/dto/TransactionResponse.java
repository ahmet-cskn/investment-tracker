package com.investmenttracker.investmentservice.transactionhistory.dto;

import com.investmenttracker.investmentservice.transactionhistory.TransactionHistory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TransactionResponse(UUID id, String name, String investmentType, BigDecimal change, LocalDate date) {

	public static TransactionResponse from(TransactionHistory entry) {
		return new TransactionResponse(entry.getId(), entry.getName(), entry.getInvestmentType(), entry.getChange(),
				entry.getDate());
	}

}
