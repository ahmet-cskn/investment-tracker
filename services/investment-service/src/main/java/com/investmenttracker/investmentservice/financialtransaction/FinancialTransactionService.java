package com.investmenttracker.investmentservice.financialtransaction;

import com.investmenttracker.investmentservice.financialtransaction.dto.CreateFinancialTransactionRequest;
import com.investmenttracker.investmentservice.financialtransaction.dto.FinancialTransactionResponse;
import com.investmenttracker.investmentservice.financialtransaction.dto.UpdateFinancialTransactionRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class FinancialTransactionService {

	// Several transactions can share a day, so name and id break the tie to keep the order stable
	private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("date"), Sort.Order.asc("name"),
			Sort.Order.asc("id"));

	private final FinancialTransactionRepository financialTransactionRepository;

	public FinancialTransactionService(FinancialTransactionRepository financialTransactionRepository) {
		this.financialTransactionRepository = financialTransactionRepository;
	}

	public FinancialTransactionResponse create(CreateFinancialTransactionRequest request) {
		FinancialTransaction entry = new FinancialTransaction(request.name(), request.change(), request.date());
		return FinancialTransactionResponse.from(financialTransactionRepository.save(entry));
	}

	@Transactional(readOnly = true)
	public List<FinancialTransactionResponse> findAll() {
		return financialTransactionRepository.findAll(NEWEST_FIRST)
				.stream()
				.map(FinancialTransactionResponse::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public FinancialTransactionResponse findById(UUID id) {
		return FinancialTransactionResponse.from(getOrThrow(id));
	}

	public FinancialTransactionResponse update(UUID id, UpdateFinancialTransactionRequest request) {
		FinancialTransaction entry = getOrThrow(id);
		entry.setName(request.name());
		entry.setChange(request.change());
		entry.setDate(request.date());
		return FinancialTransactionResponse.from(entry);
	}

	public void delete(UUID id) {
		financialTransactionRepository.delete(getOrThrow(id));
	}

	private FinancialTransaction getOrThrow(UUID id) {
		return financialTransactionRepository.findById(id).orElseThrow(() -> new FinancialTransactionNotFoundException(id));
	}

}
