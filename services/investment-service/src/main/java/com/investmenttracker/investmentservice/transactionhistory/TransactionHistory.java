package com.investmenttracker.investmentservice.transactionhistory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One entry in the history of changes to an investment. The name and type are copied from the
 * investment rather than referencing it, which keeps the history intact if the investment is later
 * renamed or deleted.
 */
@Entity
@Table(name = "transaction_history")
public class TransactionHistory {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false)
	private String name;

	@Column(name = "investment_type", nullable = false)
	private String investmentType;

	// Signed: negative when the investment decreased
	@Column(nullable = false, precision = 38, scale = 18)
	private BigDecimal change;

	// The day the change happened; the time of day is not tracked
	@Column(nullable = false)
	private LocalDate date;

	// What the change was worth in USD on that day (price times change), or null if no price could be obtained
	@Column(precision = 38, scale = 18)
	private BigDecimal worth;

	protected TransactionHistory() {
		// required by JPA
	}

	public TransactionHistory(String name, String investmentType, BigDecimal change, LocalDate date) {
		this.name = name;
		this.investmentType = investmentType;
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

	public String getInvestmentType() {
		return investmentType;
	}

	public void setInvestmentType(String investmentType) {
		this.investmentType = investmentType;
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

	public BigDecimal getWorth() {
		return worth;
	}

	public void setWorth(BigDecimal worth) {
		this.worth = worth;
	}

}
