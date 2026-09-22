package com.investmenttracker.investmentservice.catalog.dto;

import com.investmenttracker.investmentservice.catalog.InvestmentCatalogEntry;

public record CatalogEntryResponse(String name, String investmentType) {

	public static CatalogEntryResponse from(InvestmentCatalogEntry entry) {
		return new CatalogEntryResponse(entry.getName(), entry.getInvestmentType());
	}

}
