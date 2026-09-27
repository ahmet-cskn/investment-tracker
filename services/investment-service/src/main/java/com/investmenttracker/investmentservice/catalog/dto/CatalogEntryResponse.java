package com.investmenttracker.investmentservice.catalog.dto;

import com.investmenttracker.investmentservice.catalog.InvestmentCatalogEntry;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A name that can be used as the name field of an investment or a transaction")
public record CatalogEntryResponse(String name, String investmentType) {

	public static CatalogEntryResponse from(InvestmentCatalogEntry entry) {
		return new CatalogEntryResponse(entry.getName(), entry.getInvestmentType());
	}

}
