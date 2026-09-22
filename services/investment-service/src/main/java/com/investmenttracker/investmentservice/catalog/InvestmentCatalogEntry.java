package com.investmenttracker.investmentservice.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A known investment name and its type, e.g. ("Gold", "Precious Metal"). This is reference data
 * seeded by Liquibase; the application does not create, update or delete rows, only reads them.
 */
@Entity
@Table(name = "investment_catalog")
public class InvestmentCatalogEntry {

	@Id
	private String name;

	@Column(name = "investment_type", nullable = false)
	private String investmentType;

	protected InvestmentCatalogEntry() {
		// required by JPA
	}

	public InvestmentCatalogEntry(String name, String investmentType) {
		this.name = name;
		this.investmentType = investmentType;
	}

	public String getName() {
		return name;
	}

	public String getInvestmentType() {
		return investmentType;
	}

}
