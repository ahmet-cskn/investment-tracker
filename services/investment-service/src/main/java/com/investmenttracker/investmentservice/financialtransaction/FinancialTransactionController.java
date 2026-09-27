package com.investmenttracker.investmentservice.financialtransaction;

import com.investmenttracker.investmentservice.financialtransaction.dto.CreateFinancialTransactionRequest;
import com.investmenttracker.investmentservice.financialtransaction.dto.FinancialTransactionResponse;
import com.investmenttracker.investmentservice.financialtransaction.dto.UpdateFinancialTransactionRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/financial-transactions")
@Tag(name = "Financial transactions")
public class FinancialTransactionController {

	private final FinancialTransactionService financialTransactionService;

	public FinancialTransactionController(FinancialTransactionService financialTransactionService) {
		this.financialTransactionService = financialTransactionService;
	}

	@PostMapping
	public ResponseEntity<FinancialTransactionResponse> create(@Valid @RequestBody CreateFinancialTransactionRequest request) {
		FinancialTransactionResponse created = financialTransactionService.create(request);
		URI location = ServletUriComponentsBuilder.fromCurrentRequest()
				.path("/{id}")
				.buildAndExpand(created.id())
				.toUri();
		return ResponseEntity.created(location).body(created);
	}

	@GetMapping
	public List<FinancialTransactionResponse> findAll() {
		return financialTransactionService.findAll();
	}

	@GetMapping("/{id}")
	public FinancialTransactionResponse findById(@PathVariable UUID id) {
		return financialTransactionService.findById(id);
	}

	@PutMapping("/{id}")
	public FinancialTransactionResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateFinancialTransactionRequest request) {
		return financialTransactionService.update(id, request);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable UUID id) {
		financialTransactionService.delete(id);
		return ResponseEntity.noContent().build();
	}

}
