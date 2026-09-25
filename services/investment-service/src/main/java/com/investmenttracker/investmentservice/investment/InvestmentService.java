package com.investmenttracker.investmentservice.investment;

import com.investmenttracker.investmentservice.catalog.InvestmentCatalogEntry;
import com.investmenttracker.investmentservice.catalog.InvestmentCatalogRepository;
import com.investmenttracker.investmentservice.catalog.PlaceholderWorth;
import com.investmenttracker.investmentservice.catalog.UnknownInvestmentNameException;
import com.investmenttracker.investmentservice.investment.dto.CreateInvestmentRequest;
import com.investmenttracker.investmentservice.investment.dto.InvestmentResponse;
import com.investmenttracker.investmentservice.investment.dto.UpdateInvestmentRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class InvestmentService {

	private final InvestmentRepository investmentRepository;
	private final InvestmentCatalogRepository investmentCatalogRepository;

	public InvestmentService(InvestmentRepository investmentRepository,
			InvestmentCatalogRepository investmentCatalogRepository) {
		this.investmentRepository = investmentRepository;
		this.investmentCatalogRepository = investmentCatalogRepository;
	}

	public InvestmentResponse create(CreateInvestmentRequest request) {
		InvestmentCatalogEntry catalogEntry = findCatalogEntryOrThrow(request.name());
		Investment investment = new Investment(request.name(), request.amount());
		applyCatalogEntry(investment, catalogEntry);
		return InvestmentResponse.from(investmentRepository.save(investment));
	}

	@Transactional(readOnly = true)
	public List<InvestmentResponse> findAll() {
		return investmentRepository.findAll().stream().map(InvestmentResponse::from).toList();
	}

	@Transactional(readOnly = true)
	public InvestmentResponse findById(UUID id) {
		return InvestmentResponse.from(getOrThrow(id));
	}

	public InvestmentResponse update(UUID id, UpdateInvestmentRequest request) {
		InvestmentCatalogEntry catalogEntry = findCatalogEntryOrThrow(request.name());
		Investment investment = getOrThrow(id);
		investment.setName(request.name());
		investment.setAmount(request.amount());
		applyCatalogEntry(investment, catalogEntry);
		return InvestmentResponse.from(investment);
	}

	public void delete(UUID id) {
		investmentRepository.delete(getOrThrow(id));
	}

	private InvestmentCatalogEntry findCatalogEntryOrThrow(String name) {
		return investmentCatalogRepository.findById(name).orElseThrow(() -> new UnknownInvestmentNameException(name));
	}

	// investmentType and worth are derived from the catalog, never taken from the request, so a
	// client cannot set them to something inconsistent with the investment's name
	private void applyCatalogEntry(Investment investment, InvestmentCatalogEntry catalogEntry) {
		investment.setInvestmentType(catalogEntry.getInvestmentType());
		investment.setWorth(PlaceholderWorth.VALUE);
	}

	private Investment getOrThrow(UUID id) {
		return investmentRepository.findById(id).orElseThrow(() -> new InvestmentNotFoundException(id));
	}

}
