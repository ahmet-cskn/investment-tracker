package com.investmenttracker.investmentservice.investment;

import com.investmenttracker.investmentservice.investment.dto.CreateInvestmentRequest;
import com.investmenttracker.investmentservice.investment.dto.InvestmentResponse;
import com.investmenttracker.investmentservice.investment.dto.UpdateInvestmentRequest;
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
@RequestMapping("/api/investments")
@Tag(name = "Investments", description = "Initial holdings: what was already owned before any transaction. "
		+ "See Portfolio for what is currently held and what it is worth.")
public class InvestmentController {

	private final InvestmentService investmentService;

	public InvestmentController(InvestmentService investmentService) {
		this.investmentService = investmentService;
	}

	@Operation(summary = "Add an initial holding", description = "name must be one of the catalog's names; "
			+ "investmentType is derived from it, never taken from the request.")
	@ApiResponse(responseCode = "201", description = "Created")
	@ApiResponse(responseCode = "400", description = "Validation failed")
	@ApiResponse(responseCode = "404", description = "name is not in the catalog")
	@PostMapping
	public ResponseEntity<InvestmentResponse> create(@Valid @RequestBody CreateInvestmentRequest request) {
		InvestmentResponse created = investmentService.create(request);
		URI location = ServletUriComponentsBuilder.fromCurrentRequest()
				.path("/{id}")
				.buildAndExpand(created.id())
				.toUri();
		return ResponseEntity.created(location).body(created);
	}

	@Operation(summary = "List every initial holding")
	@GetMapping
	public List<InvestmentResponse> findAll() {
		return investmentService.findAll();
	}

	@Operation(summary = "Get one initial holding by id")
	@ApiResponse(responseCode = "404", description = "No investment with this id")
	@GetMapping("/{id}")
	public InvestmentResponse findById(@PathVariable UUID id) {
		return investmentService.findById(id);
	}

	@Operation(summary = "Replace an initial holding's name and amount")
	@ApiResponse(responseCode = "400", description = "Validation failed")
	@ApiResponse(responseCode = "404", description = "No investment with this id, or name is not in the catalog")
	@PutMapping("/{id}")
	public InvestmentResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateInvestmentRequest request) {
		return investmentService.update(id, request);
	}

	@Operation(summary = "Delete an initial holding")
	@ApiResponse(responseCode = "204", description = "Deleted")
	@ApiResponse(responseCode = "404", description = "No investment with this id")
	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable UUID id) {
		investmentService.delete(id);
		return ResponseEntity.noContent().build();
	}

}
