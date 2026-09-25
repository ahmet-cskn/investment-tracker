package com.investmenttracker.investmentservice.transactionhistory;

import com.investmenttracker.investmentservice.catalog.InvestmentCatalogEntry;
import com.investmenttracker.investmentservice.catalog.InvestmentCatalogRepository;
import com.investmenttracker.investmentservice.catalog.UnknownInvestmentNameException;
import com.investmenttracker.investmentservice.pricing.PriceService;
import com.investmenttracker.investmentservice.pricing.Worth;
import com.investmenttracker.investmentservice.transactionhistory.dto.CreateTransactionRequest;
import com.investmenttracker.investmentservice.transactionhistory.dto.TransactionResponse;
import com.investmenttracker.investmentservice.transactionhistory.dto.UpdateTransactionRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/**
 * Not @Transactional as a whole, on purpose: working out a transaction's worth can mean a call to the price
 * provider over HTTP, and a database transaction (with its connection) should not stay open for that.
 * Each repository call runs in its own transaction instead, and the prices are looked up in between.
 */
@Service
public class TransactionHistoryService {

	// Several transactions can share a day, so name and id break the tie to keep the order stable
	private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("date"), Sort.Order.asc("name"),
			Sort.Order.asc("id"));

	private final TransactionHistoryRepository transactionHistoryRepository;
	private final InvestmentCatalogRepository investmentCatalogRepository;
	private final PriceService priceService;

	public TransactionHistoryService(TransactionHistoryRepository transactionHistoryRepository,
			InvestmentCatalogRepository investmentCatalogRepository, PriceService priceService) {
		this.transactionHistoryRepository = transactionHistoryRepository;
		this.investmentCatalogRepository = investmentCatalogRepository;
		this.priceService = priceService;
	}

	public TransactionResponse create(CreateTransactionRequest request) {
		String investmentType = findInvestmentTypeOrThrow(request.name());
		TransactionHistory entry = new TransactionHistory(request.name(), investmentType, request.change(),
				request.date());
		entry.setWorth(worthOf(request.name(), request.date(), request.change()));
		return TransactionResponse.from(transactionHistoryRepository.save(entry));
	}

	public List<TransactionResponse> findAll() {
		return transactionHistoryRepository.findAll(NEWEST_FIRST).stream().map(TransactionResponse::from).toList();
	}

	public TransactionResponse findById(UUID id) {
		return TransactionResponse.from(getOrThrow(id));
	}

	/**
	 * Replaces the fields and works the worth out again: it depends on all three of name, change and date.
	 * Saving a transaction whose price was unavailable before is therefore also how its worth gets filled in.
	 */
	public TransactionResponse update(UUID id, UpdateTransactionRequest request) {
		String investmentType = findInvestmentTypeOrThrow(request.name());
		TransactionHistory entry = getOrThrow(id);
		BigDecimal worth = worthOf(request.name(), request.date(), request.change());

		// entry is detached (there is no surrounding transaction), so save() writes the changes back
		entry.setName(request.name());
		entry.setInvestmentType(investmentType);
		entry.setChange(request.change());
		entry.setDate(request.date());
		entry.setWorth(worth);
		transactionHistoryRepository.save(entry);
		return TransactionResponse.from(entry);
	}

	public void delete(UUID id) {
		transactionHistoryRepository.delete(getOrThrow(id));
	}

	/**
	 * The day's price times the change, or null if there is no price for that day (the provider could not be
	 * reached, or a stock older than its free history) or the result would not fit the column.
	 */
	private BigDecimal worthOf(String name, LocalDate date, BigDecimal change) {
		return Worth.of(priceService.findPrice(name, date), change).orElse(null);
	}

	// investmentType is derived from the catalog, exactly as on Investment, so it can never disagree with name
	private String findInvestmentTypeOrThrow(String name) {
		InvestmentCatalogEntry catalogEntry = investmentCatalogRepository.findById(name)
				.orElseThrow(() -> new UnknownInvestmentNameException(name));
		return catalogEntry.getInvestmentType();
	}

	private TransactionHistory getOrThrow(UUID id) {
		return transactionHistoryRepository.findById(id).orElseThrow(() -> new TransactionNotFoundException(id));
	}

}
