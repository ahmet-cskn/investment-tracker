package com.investmenttracker.investmentservice.transactionhistory;

import com.investmenttracker.investmentservice.transactionhistory.dto.CreateTransactionRequest;
import com.investmenttracker.investmentservice.transactionhistory.dto.TransactionResponse;
import com.investmenttracker.investmentservice.transactionhistory.dto.UpdateTransactionRequest;
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
@RequestMapping("/api/transactions")
@Tag(name = "Transactions", description = "Changes to an investment's amount, in the investment's own unit.")
public class TransactionHistoryController {

	private final TransactionHistoryService transactionHistoryService;

	public TransactionHistoryController(TransactionHistoryService transactionHistoryService) {
		this.transactionHistoryService = transactionHistoryService;
	}

	@Operation(summary = "Record a transaction", description = "worth is looked up from the price of date and "
			+ "stored with the transaction; the request is still saved even if no price could be obtained.")
	@ApiResponse(responseCode = "201", description = "Created")
	@ApiResponse(responseCode = "400", description = "Validation failed")
	@ApiResponse(responseCode = "404", description = "name is not in the catalog")
	@PostMapping
	public ResponseEntity<TransactionResponse> create(@Valid @RequestBody CreateTransactionRequest request) {
		TransactionResponse created = transactionHistoryService.create(request);
		URI location = ServletUriComponentsBuilder.fromCurrentRequest()
				.path("/{id}")
				.buildAndExpand(created.id())
				.toUri();
		return ResponseEntity.created(location).body(created);
	}

	@Operation(summary = "List every transaction, newest first")
	@GetMapping
	public List<TransactionResponse> findAll() {
		return transactionHistoryService.findAll();
	}

	@Operation(summary = "Get one transaction by id")
	@ApiResponse(responseCode = "404", description = "No transaction with this id")
	@GetMapping("/{id}")
	public TransactionResponse findById(@PathVariable UUID id) {
		return transactionHistoryService.findById(id);
	}

	@Operation(summary = "Replace a transaction's fields", description = "worth is worked out again, since it "
			+ "depends on name, change and date; this is also how a missing worth gets filled in.")
	@ApiResponse(responseCode = "400", description = "Validation failed")
	@ApiResponse(responseCode = "404", description = "No transaction with this id, or name is not in the catalog")
	@PutMapping("/{id}")
	public TransactionResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateTransactionRequest request) {
		return transactionHistoryService.update(id, request);
	}

	@Operation(summary = "Delete a transaction")
	@ApiResponse(responseCode = "204", description = "Deleted")
	@ApiResponse(responseCode = "404", description = "No transaction with this id")
	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable UUID id) {
		transactionHistoryService.delete(id);
		return ResponseEntity.noContent().build();
	}

}
