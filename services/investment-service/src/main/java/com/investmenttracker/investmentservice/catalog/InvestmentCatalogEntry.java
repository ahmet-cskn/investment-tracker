package com.investmenttracker.investmentservice.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A known investment: its name and type, e.g. ("Gold", "Precious Metal"), and how to price it, i.e. what
 * kind of asset it is and its symbol at the price provider. This is reference data seeded by Liquibase;
 * the application does not create, update or delete rows, only reads them.
 */
@Entity
@Table(name = "investment_catalog")
public class InvestmentCatalogEntry {

	@Id
	private String name;

	@Column(name = "investment_type", nullable = false)
	private String investmentType;

	@Enumerated(EnumType.STRING)
	@Column(name = "asset_type", nullable = false)
	private AssetType assetType;

	@Column(name = "price_symbol", nullable = false)
	private String priceSymbol;

	protected InvestmentCatalogEntry() {
		// required by JPA
	}

	public InvestmentCatalogEntry(String name, String investmentType, AssetType assetType, String priceSymbol) {
		this.name = name;
		this.investmentType = investmentType;
		this.assetType = assetType;
		this.priceSymbol = priceSymbol;
	}

	public String getName() {
		return name;
	}

	public String getInvestmentType() {
		return investmentType;
	}

	public AssetType getAssetType() {
		return assetType;
	}

	public String getPriceSymbol() {
		return priceSymbol;
	}

}
