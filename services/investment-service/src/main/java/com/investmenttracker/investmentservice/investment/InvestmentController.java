package com.investmenttracker.investmentservice.investment;

import com.investmenttracker.investmentservice.investment.dto.CreateInvestmentRequest;
import com.investmenttracker.investmentservice.investment.dto.InvestmentResponse;
import com.investmenttracker.investmentservice.investment.dto.UpdateInvestmentRequest;
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
@Tag(name = "Investments")
public class InvestmentController {

	private final InvestmentService investmentService;

	public InvestmentController(InvestmentService investmentService) {
		this.investmentService = investmentService;
	}

	@PostMapping
	public ResponseEntity<InvestmentResponse> create(@Valid @RequestBody CreateInvestmentRequest request) {
		InvestmentResponse created = investmentService.create(request);
		URI location = ServletUriComponentsBuilder.fromCurrentRequest()
				.path("/{id}")
				.buildAndExpand(created.id())
				.toUri();
		return ResponseEntity.created(location).body(created);
	}

	@GetMapping
	public List<InvestmentResponse> findAll() {
		return investmentService.findAll();
	}

	@GetMapping("/{id}")
	public InvestmentResponse findById(@PathVariable UUID id) {
		return investmentService.findById(id);
	}

	@PutMapping("/{id}")
	public InvestmentResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateInvestmentRequest request) {
		return investmentService.update(id, request);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable UUID id) {
		investmentService.delete(id);
		return ResponseEntity.noContent().build();
	}

}
