package com.investmenttracker.investmentservice.investment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "investments")
public class Investment {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false, precision = 38, scale = 18)
	private BigDecimal amount;

	// Nullable: rows created before these columns existed have neither
	@Column(name = "investment_type")
	private String investmentType;

	@Column(precision = 38, scale = 18)
	private BigDecimal worth;

	protected Investment() {
		// required by JPA
	}

	public Investment(String name, BigDecimal amount) {
		this.name = name;
		this.amount = amount;
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

	public BigDecimal getAmount() {
		return amount;
	}

	public void setAmount(BigDecimal amount) {
		this.amount = amount;
	}

	public String getInvestmentType() {
		return investmentType;
	}

	public void setInvestmentType(String investmentType) {
		this.investmentType = investmentType;
	}

	public BigDecimal getWorth() {
		return worth;
	}

	public void setWorth(BigDecimal worth) {
		this.worth = worth;
	}

}
