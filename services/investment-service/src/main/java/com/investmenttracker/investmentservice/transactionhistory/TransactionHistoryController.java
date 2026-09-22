package com.investmenttracker.investmentservice.transactionhistory;

import com.investmenttracker.investmentservice.transactionhistory.dto.CreateTransactionRequest;
import com.investmenttracker.investmentservice.transactionhistory.dto.TransactionResponse;
import com.investmenttracker.investmentservice.transactionhistory.dto.UpdateTransactionRequest;
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
@RequestMapping("/api/transactions")
@Tag(name = "Transactions")
public class TransactionHistoryController {

	private final TransactionHistoryService transactionHistoryService;

	public TransactionHistoryController(TransactionHistoryService transactionHistoryService) {
		this.transactionHistoryService = transactionHistoryService;
	}

	@PostMapping
	public ResponseEntity<TransactionResponse> create(@Valid @RequestBody CreateTransactionRequest request) {
		TransactionResponse created = transactionHistoryService.create(request);
		URI location = ServletUriComponentsBuilder.fromCurrentRequest()
				.path("/{id}")
				.buildAndExpand(created.id())
				.toUri();
		return ResponseEntity.created(location).body(created);
	}

	@GetMapping
	public List<TransactionResponse> findAll() {
		return transactionHistoryService.findAll();
	}

	@GetMapping("/{id}")
	public TransactionResponse findById(@PathVariable UUID id) {
		return transactionHistoryService.findById(id);
	}

	@PutMapping("/{id}")
	public TransactionResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateTransactionRequest request) {
		return transactionHistoryService.update(id, request);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable UUID id) {
		transactionHistoryService.delete(id);
		return ResponseEntity.noContent().build();
	}

}
