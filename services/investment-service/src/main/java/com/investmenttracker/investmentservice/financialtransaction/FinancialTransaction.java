package com.investmenttracker.investmentservice.financialtransaction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** One entry of everyday money in or out, in USD. The name is free text; it does not refer to an investment. */
@Entity
@Table(name = "financial_transaction")
public class FinancialTransaction {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false)
	private String name;

	// Signed: positive for income, negative for spending
	@Column(nullable = false, precision = 38, scale = 18)
	private BigDecimal change;

	// The day it happened; the time of day is not tracked
	@Column(nullable = false)
	private LocalDate date;

	protected FinancialTransaction() {
		// required by JPA
	}

	public FinancialTransaction(String name, BigDecimal change, LocalDate date) {
		this.name = name;
		this.change = change;
		this.date = date;
	}

	public UUID getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public BigDecimal getChange() {
		return change;
	}

	public void setChange(BigDecimal change) {
		this.change = change;
	}

	public LocalDate getDate() {
		return date;
	}

	public void setDate(LocalDate date) {
		this.date = date;
	}

}
