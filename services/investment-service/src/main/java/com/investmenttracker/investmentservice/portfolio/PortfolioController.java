package com.investmenttracker.investmentservice.portfolio;

import com.investmenttracker.investmentservice.portfolio.dto.PortfolioEntryResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only: the portfolio is derived from the initial investments and the transactions, never edited. */
@RestController
@RequestMapping("/api/portfolio")
@Tag(name = "Portfolio")
public class PortfolioController {

	private final PortfolioService portfolioService;

	public PortfolioController(PortfolioService portfolioService) {
		this.portfolioService = portfolioService;
	}

	@GetMapping
	public List<PortfolioEntryResponse> getPortfolio() {
		return portfolioService.getPortfolio();
	}

}
