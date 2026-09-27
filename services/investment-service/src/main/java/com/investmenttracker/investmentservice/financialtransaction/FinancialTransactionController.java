package com.investmenttracker.investmentservice.financialtransaction;

import com.investmenttracker.investmentservice.financialtransaction.dto.CreateFinancialTransactionRequest;
import com.investmenttracker.investmentservice.financialtransaction.dto.FinancialTransactionResponse;
import com.investmenttracker.investmentservice.financialtransaction.dto.UpdateFinancialTransactionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
@Tag(name = "Financial transactions", description = "Everyday income and spending, unrelated to any investment; "
		+ "never affects the investments, the transactions or the portfolio.")
public class FinancialTransactionController {

	private final FinancialTransactionService financialTransactionService;

	public FinancialTransactionController(FinancialTransactionService financialTransactionService) {
		this.financialTransactionService = financialTransactionService;
	}

	@Operation(summary = "Record a financial transaction")
	@ApiResponse(responseCode = "201", description = "Created")
	@ApiResponse(responseCode = "400", description = "Validation failed")
	@PostMapping
	public ResponseEntity<FinancialTransactionResponse> create(@Valid @RequestBody CreateFinancialTransactionRequest request) {
		FinancialTransactionResponse created = financialTransactionService.create(request);
		URI location = ServletUriComponentsBuilder.fromCurrentRequest()
				.path("/{id}")
				.buildAndExpand(created.id())
				.toUri();
		return ResponseEntity.created(location).body(created);
	}

	@Operation(summary = "List every financial transaction, newest first")
	@GetMapping
	public List<FinancialTransactionResponse> findAll() {
		return financialTransactionService.findAll();
	}

	@Operation(summary = "Get one financial transaction by id")
	@ApiResponse(responseCode = "404", description = "No financial transaction with this id")
	@GetMapping("/{id}")
	public FinancialTransactionResponse findById(@PathVariable UUID id) {
		return financialTransactionService.findById(id);
	}

	@Operation(summary = "Replace a financial transaction's fields")
	@ApiResponse(responseCode = "400", description = "Validation failed")
	@ApiResponse(responseCode = "404", description = "No financial transaction with this id")
	@PutMapping("/{id}")
	public FinancialTransactionResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateFinancialTransactionRequest request) {
		return financialTransactionService.update(id, request);
	}

	@Operation(summary = "Delete a financial transaction")
	@ApiResponse(responseCode = "204", description = "Deleted")
	@ApiResponse(responseCode = "404", description = "No financial transaction with this id")
	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable UUID id) {
		financialTransactionService.delete(id);
		return ResponseEntity.noContent().build();
	}

}
