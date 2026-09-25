package com.investmenttracker.investmentservice.transactionhistory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.investmenttracker.investmentservice.catalog.AssetType;
import com.investmenttracker.investmentservice.catalog.InvestmentCatalogEntry;
import com.investmenttracker.investmentservice.catalog.InvestmentCatalogRepository;
import com.investmenttracker.investmentservice.catalog.UnknownInvestmentNameException;
import com.investmenttracker.investmentservice.pricing.PriceService;
import com.investmenttracker.investmentservice.transactionhistory.dto.CreateTransactionRequest;
import com.investmenttracker.investmentservice.transactionhistory.dto.TransactionResponse;
import com.investmenttracker.investmentservice.transactionhistory.dto.UpdateTransactionRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
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

	@Mock
	private PriceService priceService;

	@InjectMocks
	private TransactionHistoryService transactionHistoryService;

	@BeforeEach
	void stubKnownCatalogEntries() {
		org.mockito.Mockito.lenient()
				.when(investmentCatalogRepository.findById("Gold"))
				.thenReturn(Optional.of(new InvestmentCatalogEntry("Gold", "Precious Metal", AssetType.METAL, "GOLD")));
		org.mockito.Mockito.lenient()
				.when(investmentCatalogRepository.findById("Bitcoin"))
				.thenReturn(Optional.of(new InvestmentCatalogEntry("Bitcoin", "Cryptocurrency", AssetType.CRYPTO, "BTC")));
	}

	@Test
	void createLooksUpTheCatalogAndDerivesInvestmentType() {
		when(transactionHistoryRepository.save(any(TransactionHistory.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
		LocalDate date = LocalDate.parse("2026-01-01");

		TransactionResponse response = transactionHistoryService
				.create(new CreateTransactionRequest("Gold", new BigDecimal("2.5"), date));

		assertThat(response.name()).isEqualTo("Gold");
		assertThat(response.investmentType()).isEqualTo("Precious Metal");
		assertThat(response.change()).isEqualByComparingTo("2.5");
		assertThat(response.date()).isEqualTo(date);
	}

	@Test
	void createAcceptsANegativeChange() {
		when(transactionHistoryRepository.save(any(TransactionHistory.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		TransactionResponse response = transactionHistoryService
				.create(new CreateTransactionRequest("Bitcoin", new BigDecimal("-1.5"), LocalDate.now()));

		assertThat(response.change()).isEqualByComparingTo("-1.5");
	}

	@Test
	void createThrowsForAnUnknownName() {
		when(investmentCatalogRepository.findById("Doge")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> transactionHistoryService
				.create(new CreateTransactionRequest("Doge", BigDecimal.ONE, LocalDate.now())))
				.isInstanceOf(UnknownInvestmentNameException.class)
				.hasMessageContaining("Doge");
	}

	@Test
	void findAllSortsByDateNewestFirstWithNameAndIdBreakingTies() {
		when(transactionHistoryRepository.findAll(
				Sort.by(Sort.Order.desc("date"), Sort.Order.asc("name"), Sort.Order.asc("id")))).thenReturn(List.of(
				new TransactionHistory("Bitcoin", "Cryptocurrency", BigDecimal.ONE, LocalDate.parse("2026-01-02")),
				new TransactionHistory("Gold", "Precious Metal", BigDecimal.ONE, LocalDate.parse("2026-01-01"))));

		List<TransactionResponse> responses = transactionHistoryService.findAll();

		assertThat(responses).extracting(TransactionResponse::name).containsExactly("Bitcoin", "Gold");
	}

	@Test
	void findByIdReturnsEntry() {
		UUID id = UUID.randomUUID();
		when(transactionHistoryRepository.findById(id))
				.thenReturn(Optional.of(new TransactionHistory("Gold", "Precious Metal", BigDecimal.ONE, LocalDate.now())));

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
				LocalDate.parse("2026-01-01"));
		when(transactionHistoryRepository.findById(id)).thenReturn(Optional.of(existing));
		LocalDate newDate = LocalDate.parse("2026-02-01");

		TransactionResponse response = transactionHistoryService
				.update(id, new UpdateTransactionRequest("Bitcoin", new BigDecimal("-3"), newDate));

		assertThat(response.name()).isEqualTo("Bitcoin");
		assertThat(response.investmentType()).isEqualTo("Cryptocurrency");
		assertThat(response.change()).isEqualByComparingTo("-3");
		assertThat(response.date()).isEqualTo(newDate);
	}

	@Test
	void updateThrowsForAnUnknownName() {
		UUID id = UUID.randomUUID();
		when(investmentCatalogRepository.findById("Doge")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> transactionHistoryService
				.update(id, new UpdateTransactionRequest("Doge", BigDecimal.ONE, LocalDate.now())))
				.isInstanceOf(UnknownInvestmentNameException.class);
	}

	@Test
	void updateThrowsWhenMissing() {
		UUID id = UUID.randomUUID();
		when(transactionHistoryRepository.findById(id)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> transactionHistoryService
				.update(id, new UpdateTransactionRequest("Gold", BigDecimal.ONE, LocalDate.now())))
				.isInstanceOf(TransactionNotFoundException.class);
	}

	@Test
	void deleteRemovesExistingEntry() {
		UUID id = UUID.randomUUID();
		TransactionHistory existing = new TransactionHistory("Gold", "Precious Metal", BigDecimal.ONE, LocalDate.now());
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

	private void priceOf(String name, LocalDate date, String price) {
		when(priceService.findPrice(name, date)).thenReturn(Optional.of(new BigDecimal(price)));
	}

	private void saveReturnsWhatItIsGiven() {
		when(transactionHistoryRepository.save(any(TransactionHistory.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
	}

	@Test
	void createStoresTheDaysPriceTimesTheChangeAsWorth() {
		LocalDate date = LocalDate.parse("2026-01-15");
		priceOf("Gold", date, "100.5");
		saveReturnsWhatItIsGiven();

		TransactionResponse response = transactionHistoryService
				.create(new CreateTransactionRequest("Gold", new BigDecimal("2"), date));

		assertThat(response.worth()).isEqualByComparingTo("201");
	}

	@Test
	void aDecreaseIsWorthANegativeAmount() {
		LocalDate date = LocalDate.parse("2026-01-15");
		priceOf("Bitcoin", date, "50000");
		saveReturnsWhatItIsGiven();

		TransactionResponse response = transactionHistoryService
				.create(new CreateTransactionRequest("Bitcoin", new BigDecimal("-1.5"), date));

		assertThat(response.worth()).isEqualByComparingTo("-75000");
	}

	@Test
	void aChangeOfZeroIsWorthZero() {
		LocalDate date = LocalDate.parse("2026-01-15");
		priceOf("Gold", date, "100");
		saveReturnsWhatItIsGiven();

		TransactionResponse response = transactionHistoryService
				.create(new CreateTransactionRequest("Gold", BigDecimal.ZERO, date));

		assertThat(response.worth()).isEqualByComparingTo("0");
	}

	@Test
	void theWorthIsRoundedToTheColumnsEighteenDecimals() {
		LocalDate date = LocalDate.parse("2026-01-15");
		// 18 decimals times 18 decimals is 36 decimals, which the NUMERIC(38,18) column would round anyway
		priceOf("Gold", date, "0.333333333333333333");
		saveReturnsWhatItIsGiven();

		TransactionResponse response = transactionHistoryService
				.create(new CreateTransactionRequest("Gold", new BigDecimal("0.333333333333333333"), date));

		assertThat(response.worth().scale()).isEqualTo(18);
		assertThat(response.worth()).isEqualByComparingTo("0.111111111111111111");
	}

	@Test
	void withoutAPriceTheTransactionIsStillSavedWithNoWorth() {
		LocalDate date = LocalDate.parse("2026-01-15");
		when(priceService.findPrice("Gold", date)).thenReturn(Optional.empty());
		saveReturnsWhatItIsGiven();

		TransactionResponse response = transactionHistoryService
				.create(new CreateTransactionRequest("Gold", new BigDecimal("2"), date));

		assertThat(response.worth()).isNull();
		assertThat(response.name()).isEqualTo("Gold");
		verify(transactionHistoryRepository).save(any(TransactionHistory.class));
	}

	@Test
	void aWorthTooLargeForTheColumnIsLeftEmptyInsteadOfFailingTheSave() {
		LocalDate date = LocalDate.parse("2026-01-15");
		priceOf("Gold", date, "100");
		saveReturnsWhatItIsGiven();

		// 20 digits times 100 is 22 digits, but only 20 fit before the decimal point
		TransactionResponse response = transactionHistoryService.create(
				new CreateTransactionRequest("Gold", new BigDecimal("99999999999999999999"), date));

		assertThat(response.worth()).isNull();
	}

	@Test
	void theLargestWorthThatFitsIsKept() {
		LocalDate date = LocalDate.parse("2026-01-15");
		priceOf("Gold", date, "1");
		saveReturnsWhatItIsGiven();

		TransactionResponse response = transactionHistoryService.create(
				new CreateTransactionRequest("Gold", new BigDecimal("99999999999999999999"), date));

		assertThat(response.worth()).isEqualByComparingTo("99999999999999999999");
	}

	@Test
	void noPriceIsLookedUpForAnUnknownName() {
		when(investmentCatalogRepository.findById("Doge")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> transactionHistoryService
				.create(new CreateTransactionRequest("Doge", BigDecimal.ONE, LocalDate.now())))
				.isInstanceOf(UnknownInvestmentNameException.class);

		verify(priceService, never()).findPrice(anyString(), any());
	}

	@Test
	void updateWorksTheWorthOutAgainFromTheNewNameChangeAndDate() {
		UUID id = UUID.randomUUID();
		TransactionHistory existing = new TransactionHistory("Gold", "Precious Metal", new BigDecimal("1"),
				LocalDate.parse("2026-01-01"));
		existing.setWorth(new BigDecimal("100"));
		when(transactionHistoryRepository.findById(id)).thenReturn(Optional.of(existing));
		LocalDate newDate = LocalDate.parse("2026-02-01");
		priceOf("Bitcoin", newDate, "50000");

		TransactionResponse response = transactionHistoryService
				.update(id, new UpdateTransactionRequest("Bitcoin", new BigDecimal("-3"), newDate));

		assertThat(response.worth()).isEqualByComparingTo("-150000");
		assertThat(existing.getWorth()).isEqualByComparingTo("-150000");
		verify(transactionHistoryRepository).save(existing);
	}

	@Test
	void updateClearsAnOldWorthWhenThereIsNoPriceForTheNewDay() {
		UUID id = UUID.randomUUID();
		TransactionHistory existing = new TransactionHistory("Gold", "Precious Metal", new BigDecimal("1"),
				LocalDate.parse("2026-01-01"));
		existing.setWorth(new BigDecimal("100"));
		when(transactionHistoryRepository.findById(id)).thenReturn(Optional.of(existing));
		when(priceService.findPrice(anyString(), any())).thenReturn(Optional.empty());

		TransactionResponse response = transactionHistoryService
				.update(id, new UpdateTransactionRequest("Gold", new BigDecimal("1"), LocalDate.parse("1990-01-01")));

		assertThat(response.worth()).isNull();
		assertThat(existing.getWorth()).isNull();
	}

	@Test
	void updateFillsInAWorthThatWasMissingBefore() {
		UUID id = UUID.randomUUID();
		LocalDate date = LocalDate.parse("2026-01-01");
		TransactionHistory existing = new TransactionHistory("Gold", "Precious Metal", new BigDecimal("2"), date);
		when(transactionHistoryRepository.findById(id)).thenReturn(Optional.of(existing));
		priceOf("Gold", date, "100");

		TransactionResponse response = transactionHistoryService
				.update(id, new UpdateTransactionRequest("Gold", new BigDecimal("2"), date));

		assertThat(response.worth()).isEqualByComparingTo("200");
	}

	@Test
	void noPriceIsLookedUpForATransactionThatDoesNotExist() {
		UUID id = UUID.randomUUID();
		when(transactionHistoryRepository.findById(id)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> transactionHistoryService
				.update(id, new UpdateTransactionRequest("Gold", BigDecimal.ONE, LocalDate.now())))
				.isInstanceOf(TransactionNotFoundException.class);

		verify(priceService, never()).findPrice(anyString(), any());
	}

}
