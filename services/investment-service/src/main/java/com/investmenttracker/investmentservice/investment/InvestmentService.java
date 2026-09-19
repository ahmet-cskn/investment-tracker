package com.investmenttracker.investmentservice.investment;

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

	public InvestmentService(InvestmentRepository investmentRepository) {
		this.investmentRepository = investmentRepository;
	}

	public InvestmentResponse create(CreateInvestmentRequest request) {
		Investment investment = new Investment(request.name(), request.amount());
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
		Investment investment = getOrThrow(id);
		investment.setName(request.name());
		investment.setAmount(request.amount());
		return InvestmentResponse.from(investment);
	}

	public void delete(UUID id) {
		investmentRepository.delete(getOrThrow(id));
	}

	private Investment getOrThrow(UUID id) {
		return investmentRepository.findById(id).orElseThrow(() -> new InvestmentNotFoundException(id));
	}

}
