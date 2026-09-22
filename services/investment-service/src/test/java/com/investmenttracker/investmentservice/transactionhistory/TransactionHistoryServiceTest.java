package com.investmenttracker.investmentservice.transactionhistory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.investmenttracker.investmentservice.catalog.InvestmentCatalogEntry;
import com.investmenttracker.investmentservice.catalog.InvestmentCatalogRepository;
import com.investmenttracker.investmentservice.catalog.UnknownInvestmentNameException;
import com.investmenttracker.investmentservice.transactionhistory.dto.CreateTransactionRequest;
import com.investmenttracker.investmentservice.transactionhistory.dto.TransactionResponse;
import com.investmenttracker.investmentservice.transactionhistory.dto.UpdateTransactionRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class TransactionHistoryServiceTest {

	@Mock
	private TransactionHistoryRepository transactionHistoryRepository;

	@Mock
	private InvestmentCatalogRepository investmentCatalogRepository;

	@InjectMocks
	private TransactionHistoryService transactionHistoryService;

	@BeforeEach
	void stubKnownCatalogEntries() {
		org.mockito.Mockito.lenient()
				.when(investmentCatalogRepository.findById("Gold"))
				.thenReturn(Optional.of(new InvestmentCatalogEntry("Gold", "Precious Metal")));
		org.mockito.Mockito.lenient()
				.when(investmentCatalogRepository.findById("Bitcoin"))
				.thenReturn(Optional.of(new InvestmentCatalogEntry("Bitcoin", "Cryptocurrency")));
	}

	@Test
	void createLooksUpTheCatalogAndDerivesInvestmentType() {
		when(transactionHistoryRepository.save(any(TransactionHistory.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
		Instant timestamp = Instant.parse("2026-01-01T00:00:00Z");

		TransactionResponse response = transactionHistoryService
				.create(new CreateTransactionRequest("Gold", new BigDecimal("2.5"), timestamp));

		assertThat(response.name()).isEqualTo("Gold");
		assertThat(response.investmentType()).isEqualTo("Precious Metal");
		assertThat(response.change()).isEqualByComparingTo("2.5");
		assertThat(response.timestamp()).isEqualTo(timestamp);
	}

	@Test
	void createAcceptsANegativeChange() {
		when(transactionHistoryRepository.save(any(TransactionHistory.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		TransactionResponse response = transactionHistoryService
				.create(new CreateTransactionRequest("Bitcoin", new BigDecimal("-1.5"), Instant.now()));

		assertThat(response.change()).isEqualByComparingTo("-1.5");
	}

	@Test
	void createThrowsForAnUnknownName() {
		when(investmentCatalogRepository.findById("Doge")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> transactionHistoryService
				.create(new CreateTransactionRequest("Doge", BigDecimal.ONE, Instant.now())))
				.isInstanceOf(UnknownInvestmentNameException.class)
				.hasMessageContaining("Doge");
	}

	@Test
	void findAllSortsByTimestampNewestFirst() {
		when(transactionHistoryRepository.findAll(Sort.by(Sort.Direction.DESC, "timestamp"))).thenReturn(List.of(
				new TransactionHistory("Bitcoin", "Cryptocurrency", BigDecimal.ONE, Instant.parse("2026-01-02T00:00:00Z")),
				new TransactionHistory("Gold", "Precious Metal", BigDecimal.ONE, Instant.parse("2026-01-01T00:00:00Z"))));

		List<TransactionResponse> responses = transactionHistoryService.findAll();

		assertThat(responses).extracting(TransactionResponse::name).containsExactly("Bitcoin", "Gold");
	}

	@Test
	void findByIdReturnsEntry() {
		UUID id = UUID.randomUUID();
		when(transactionHistoryRepository.findById(id))
				.thenReturn(Optional.of(new TransactionHistory("Gold", "Precious Metal", BigDecimal.ONE, Instant.now())));

		assertThat(transactionHistoryService.findById(id).name()).isEqualTo("Gold");
	}

	@Test
	void findByIdThrowsWhenMissing() {
		UUID id = UUID.randomUUID();
		when(transactionHistoryRepository.findById(id)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> transactionHistoryService.findById(id))
				.isInstanceOf(TransactionNotFoundException.class)
				.hasMessageContaining(id.toString());
	}

	@Test
	void updateChangesEveryFieldAndRederivesType() {
		UUID id = UUID.randomUUID();
		TransactionHistory existing = new TransactionHistory("Gold", "Precious Metal", new BigDecimal("1"),
				Instant.parse("2026-01-01T00:00:00Z"));
		when(transactionHistoryRepository.findById(id)).thenReturn(Optional.of(existing));
		Instant newTimestamp = Instant.parse("2026-02-01T00:00:00Z");

		TransactionResponse response = transactionHistoryService
				.update(id, new UpdateTransactionRequest("Bitcoin", new BigDecimal("-3"), newTimestamp));

		assertThat(response.name()).isEqualTo("Bitcoin");
		assertThat(response.investmentType()).isEqualTo("Cryptocurrency");
		assertThat(response.change()).isEqualByComparingTo("-3");
		assertThat(response.timestamp()).isEqualTo(newTimestamp);
	}

	@Test
	void updateThrowsForAnUnknownName() {
		UUID id = UUID.randomUUID();
		when(investmentCatalogRepository.findById("Doge")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> transactionHistoryService
				.update(id, new UpdateTransactionRequest("Doge", BigDecimal.ONE, Instant.now())))
				.isInstanceOf(UnknownInvestmentNameException.class);
	}

	@Test
	void updateThrowsWhenMissing() {
		UUID id = UUID.randomUUID();
		when(transactionHistoryRepository.findById(id)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> transactionHistoryService
				.update(id, new UpdateTransactionRequest("Gold", BigDecimal.ONE, Instant.now())))
				.isInstanceOf(TransactionNotFoundException.class);
	}

	@Test
	void deleteRemovesExistingEntry() {
		UUID id = UUID.randomUUID();
		TransactionHistory existing = new TransactionHistory("Gold", "Precious Metal", BigDecimal.ONE, Instant.now());
		when(transactionHistoryRepository.findById(id)).thenReturn(Optional.of(existing));

		transactionHistoryService.delete(id);

		verify(transactionHistoryRepository).delete(existing);
	}

	@Test
	void deleteThrowsWhenMissing() {
		UUID id = UUID.randomUUID();
		when(transactionHistoryRepository.findById(id)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> transactionHistoryService.delete(id)).isInstanceOf(TransactionNotFoundException.class);
		verify(transactionHistoryRepository, never()).delete(any());
	}

}
