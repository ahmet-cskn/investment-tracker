package com.investmenttracker.investmentservice.portfolio;

import com.investmenttracker.investmentservice.catalog.InvestmentCatalogEntry;
import com.investmenttracker.investmentservice.catalog.InvestmentCatalogRepository;
import com.investmenttracker.investmentservice.portfolio.dto.PortfolioEntryResponse;
import com.investmenttracker.investmentservice.pricing.PriceService;
import com.investmenttracker.investmentservice.pricing.Worth;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Not @Transactional as a whole, on purpose: valuing a holding can mean a call to the price provider over HTTP,
 * and a database transaction (with its connection) should not stay open for that. The reads are each their own.
 */
@Service
public class PortfolioService {

	private final PortfolioRepository portfolioRepository;
	private final InvestmentCatalogRepository investmentCatalogRepository;
	private final PriceService priceService;
	private final Clock clock;

	public PortfolioService(PortfolioRepository portfolioRepository,
			InvestmentCatalogRepository investmentCatalogRepository, PriceService priceService, Clock clock) {
		this.portfolioRepository = portfolioRepository;
		this.investmentCatalogRepository = investmentCatalogRepository;
		this.priceService = priceService;
		this.clock = clock;
	}

	/**
	 * One entry per investment name that appears in either the initial investments or the transaction
	 * history, sorted by name. A total of zero, or a negative one, is still returned as is. The worth is the
	 * amount at the latest price, or null when no price could be obtained.
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

		// A name missing from the catalog (only possible if a row was inserted around the API) gets no type or worth
		LocalDate today = LocalDate.now(clock);
		return totals.entrySet()
				.stream()
				.map(entry -> new PortfolioEntryResponse(entry.getKey(), typeByName.get(entry.getKey()), entry.getValue(),
						worthOf(entry.getKey(), today, entry.getValue(), typeByName.containsKey(entry.getKey()))))
				.toList();
	}

	// The price service rejects a name outside the catalog, which the portfolio tolerates, so it is skipped
	private BigDecimal worthOf(String name, LocalDate today, BigDecimal amount, boolean inCatalog) {
		if (!inCatalog) {
			return null;
		}
		return Worth.of(priceService.findPrice(name, today), amount).orElse(null);
	}

}
