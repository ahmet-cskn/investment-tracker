package com.investmenttracker.investmentservice.financialtransaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.investmenttracker.investmentservice.financialtransaction.dto.CreateFinancialTransactionRequest;
import com.investmenttracker.investmentservice.financialtransaction.dto.FinancialTransactionResponse;
import com.investmenttracker.investmentservice.financialtransaction.dto.UpdateFinancialTransactionRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class FinancialTransactionServiceTest {

	private static final LocalDate DAY = LocalDate.parse("2026-01-15");

	@Mock
	private FinancialTransactionRepository financialTransactionRepository;

	@InjectMocks
	private FinancialTransactionService financialTransactionService;

	@Test
	void createSavesTheGivenFields() {
		when(financialTransactionRepository.save(any(FinancialTransaction.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		FinancialTransactionResponse response = financialTransactionService
				.create(new CreateFinancialTransactionRequest("Groceries", new BigDecimal("-42.5"), DAY));

		assertThat(response.name()).isEqualTo("Groceries");
		assertThat(response.change()).isEqualByComparingTo("-42.5");
		assertThat(response.date()).isEqualTo(DAY);
	}

	@Test
	void findAllListsNewestFirstWithNameAndIdBreakingTies() {
		when(financialTransactionRepository.findAll(any(Sort.class))).thenReturn(List.of());

		financialTransactionService.findAll();

		verify(financialTransactionRepository).findAll(Sort.by(Sort.Order.desc("date"), Sort.Order.asc("name"),
				Sort.Order.asc("id")));
	}

	@Test
	void findByIdReturnsTheTransaction() {
		UUID id = UUID.randomUUID();
		when(financialTransactionRepository.findById(id))
				.thenReturn(Optional.of(new FinancialTransaction("Salary", new BigDecimal("3000"), DAY)));

		assertThat(financialTransactionService.findById(id).name()).isEqualTo("Salary");
	}

	@Test
	void findByIdThrowsWhenMissing() {
		UUID id = UUID.randomUUID();
		when(financialTransactionRepository.findById(id)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> financialTransactionService.findById(id))
				.isInstanceOf(FinancialTransactionNotFoundException.class)
				.hasMessageContaining(id.toString());
	}

	@Test
	void updateReplacesEveryField() {
		UUID id = UUID.randomUUID();
		FinancialTransaction existing = new FinancialTransaction("Groceries", new BigDecimal("-42.5"), DAY);
		when(financialTransactionRepository.findById(id)).thenReturn(Optional.of(existing));

		FinancialTransactionResponse response = financialTransactionService.update(id,
				new UpdateFinancialTransactionRequest("Salary", new BigDecimal("3000"), LocalDate.parse("2026-02-01")));

		assertThat(response.name()).isEqualTo("Salary");
		assertThat(response.change()).isEqualByComparingTo("3000");
		assertThat(response.date()).isEqualTo(LocalDate.parse("2026-02-01"));
		assertThat(existing.getName()).isEqualTo("Salary");
	}

	@Test
	void updateThrowsWhenMissing() {
		UUID id = UUID.randomUUID();
		when(financialTransactionRepository.findById(id)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> financialTransactionService.update(id,
				new UpdateFinancialTransactionRequest("x", BigDecimal.ONE, DAY)))
				.isInstanceOf(FinancialTransactionNotFoundException.class);
	}

	@Test
	void deleteRemovesTheTransaction() {
		UUID id = UUID.randomUUID();
		FinancialTransaction existing = new FinancialTransaction("Groceries", BigDecimal.ONE, DAY);
		when(financialTransactionRepository.findById(id)).thenReturn(Optional.of(existing));

		financialTransactionService.delete(id);

		verify(financialTransactionRepository).delete(existing);
	}

	@Test
	void deleteThrowsWhenMissing() {
		UUID id = UUID.randomUUID();
		when(financialTransactionRepository.findById(id)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> financialTransactionService.delete(id))
				.isInstanceOf(FinancialTransactionNotFoundException.class);
		verify(financialTransactionRepository, never()).delete(any());
	}

}
