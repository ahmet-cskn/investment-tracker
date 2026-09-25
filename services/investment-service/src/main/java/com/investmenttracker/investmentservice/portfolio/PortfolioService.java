package com.investmenttracker.investmentservice.portfolio;

import com.investmenttracker.investmentservice.catalog.InvestmentCatalogEntry;
import com.investmenttracker.investmentservice.catalog.InvestmentCatalogRepository;
import com.investmenttracker.investmentservice.catalog.PlaceholderWorth;
import com.investmenttracker.investmentservice.portfolio.dto.PortfolioEntryResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PortfolioService {

	private final PortfolioRepository portfolioRepository;
	private final InvestmentCatalogRepository investmentCatalogRepository;

	public PortfolioService(PortfolioRepository portfolioRepository,
			InvestmentCatalogRepository investmentCatalogRepository) {
		this.portfolioRepository = portfolioRepository;
		this.investmentCatalogRepository = investmentCatalogRepository;
	}

	/**
	 * One entry per investment name that appears in either the initial investments or the transaction
	 * history, sorted by name. A total of zero, or a negative one, is still returned as is.
	 */
	public List<PortfolioEntryResponse> getPortfolio() {
		// A TreeMap keeps the result sorted by name
		Map<String, BigDecimal> totals = new TreeMap<>();
		portfolioRepository.sumInitialAmountsByName().forEach(row -> totals.merge(row.name(), row.total(), BigDecimal::add));
		portfolioRepository.sumChangesByName().forEach(row -> totals.merge(row.name(), row.total(), BigDecimal::add));

		Map<String, String> typeByName = investmentCatalogRepository.findAllById(totals.keySet())
				.stream()
				.collect(Collectors.toMap(InvestmentCatalogEntry::getName, InvestmentCatalogEntry::getInvestmentType,
						(first, second) -> first));

		// A name missing from the catalog (only possible if a row was inserted around the API) gets no type
		return totals.entrySet()
				.stream()
				.map(entry -> new PortfolioEntryResponse(entry.getKey(), typeByName.get(entry.getKey()), entry.getValue(),
						PlaceholderWorth.VALUE))
				.toList();
	}

}
