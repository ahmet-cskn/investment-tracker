package com.investmenttracker.investmentservice.transactionhistory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One entry in the history of changes to an investment. Entries are records of things that happened,
 * so there are no setters. The name and type are copied from the investment rather than referencing it,
 * which keeps the history intact if the investment is later deleted.
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

	@Column(nullable = false)
	private Instant timestamp;

	protected TransactionHistory() {
		// required by JPA
	}

	public TransactionHistory(String name, String investmentType, BigDecimal change, Instant timestamp) {
		this.name = name;
		this.investmentType = investmentType;
		this.change = change;
		this.timestamp = timestamp;
	}

	public UUID getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getInvestmentType() {
		return investmentType;
	}

	public BigDecimal getChange() {
		return change;
	}

	public Instant getTimestamp() {
		return timestamp;
	}

}
