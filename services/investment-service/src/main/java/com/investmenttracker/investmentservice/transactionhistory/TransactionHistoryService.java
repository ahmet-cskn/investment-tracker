package com.investmenttracker.investmentservice.transactionhistory;

import com.investmenttracker.investmentservice.catalog.InvestmentCatalogEntry;
import com.investmenttracker.investmentservice.catalog.InvestmentCatalogRepository;
import com.investmenttracker.investmentservice.catalog.UnknownInvestmentNameException;
import com.investmenttracker.investmentservice.transactionhistory.dto.CreateTransactionRequest;
import com.investmenttracker.investmentservice.transactionhistory.dto.TransactionResponse;
import com.investmenttracker.investmentservice.transactionhistory.dto.UpdateTransactionRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TransactionHistoryService {

	private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "timestamp");

	private final TransactionHistoryRepository transactionHistoryRepository;
	private final InvestmentCatalogRepository investmentCatalogRepository;

	public TransactionHistoryService(TransactionHistoryRepository transactionHistoryRepository,
			InvestmentCatalogRepository investmentCatalogRepository) {
		this.transactionHistoryRepository = transactionHistoryRepository;
		this.investmentCatalogRepository = investmentCatalogRepository;
	}

	public TransactionResponse create(CreateTransactionRequest request) {
		String investmentType = findInvestmentTypeOrThrow(request.name());
		TransactionHistory entry = new TransactionHistory(request.name(), investmentType, request.change(),
				request.timestamp());
		return TransactionResponse.from(transactionHistoryRepository.save(entry));
	}

	@Transactional(readOnly = true)
	public List<TransactionResponse> findAll() {
		return transactionHistoryRepository.findAll(NEWEST_FIRST).stream().map(TransactionResponse::from).toList();
	}

	@Transactional(readOnly = true)
	public TransactionResponse findById(UUID id) {
		return TransactionResponse.from(getOrThrow(id));
	}

	public TransactionResponse update(UUID id, UpdateTransactionRequest request) {
		String investmentType = findInvestmentTypeOrThrow(request.name());
		TransactionHistory entry = getOrThrow(id);
		entry.setName(request.name());
		entry.setInvestmentType(investmentType);
		entry.setChange(request.change());
		entry.setTimestamp(request.timestamp());
		return TransactionResponse.from(entry);
	}

	public void delete(UUID id) {
		transactionHistoryRepository.delete(getOrThrow(id));
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
