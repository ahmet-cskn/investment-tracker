package com.investmenttracker.investmentservice.catalog;

import com.investmenttracker.investmentservice.catalog.dto.CatalogEntryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only list of known investment names and their types, e.g. for populating a dropdown. */
@RestController
@RequestMapping("/api/catalog")
@Tag(name = "Catalog", description = "The fixed list of investment names the app knows prices for.")
public class CatalogController {

	private final InvestmentCatalogRepository investmentCatalogRepository;

	public CatalogController(InvestmentCatalogRepository investmentCatalogRepository) {
		this.investmentCatalogRepository = investmentCatalogRepository;
	}

	@Operation(summary = "List every known investment name and its type, sorted by name")
	@GetMapping
	public List<CatalogEntryResponse> findAll() {
		return investmentCatalogRepository.findAll(Sort.by("name")).stream().map(CatalogEntryResponse::from).toList();
	}

}
