package com.investmenttracker.investmentservice.financialtransaction.dto;

import com.investmenttracker.investmentservice.financialtransaction.FinancialTransaction;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record FinancialTransactionResponse(UUID id, String name, BigDecimal change, LocalDate date) {

	public static FinancialTransactionResponse from(FinancialTransaction entry) {
		return new FinancialTransactionResponse(entry.getId(), entry.getName(), entry.getChange(), entry.getDate());
	}

}
