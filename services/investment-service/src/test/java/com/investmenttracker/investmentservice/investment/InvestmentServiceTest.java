package com.investmenttracker.investmentservice.investment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.investmenttracker.investmentservice.catalog.InvestmentCatalogEntry;
import com.investmenttracker.investmentservice.catalog.InvestmentCatalogRepository;
import com.investmenttracker.investmentservice.catalog.UnknownInvestmentNameException;
import com.investmenttracker.investmentservice.investment.dto.CreateInvestmentRequest;
import com.investmenttracker.investmentservice.investment.dto.InvestmentResponse;
import com.investmenttracker.investmentservice.investment.dto.UpdateInvestmentRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InvestmentServiceTest {

	@Mock
	private InvestmentRepository investmentRepository;

	@Mock
	private InvestmentCatalogRepository investmentCatalogRepository;

	@InjectMocks
	private InvestmentService investmentService;

	@BeforeEach
	void stubKnownCatalogEntries() {
		// lenient: not every test in this class exercises the catalog lookup
		lenient().when(investmentCatalogRepository.findById("Gold"))
				.thenReturn(Optional.of(new InvestmentCatalogEntry("Gold", "Precious Metal")));
		lenient().when(investmentCatalogRepository.findById("Silver"))
				.thenReturn(Optional.of(new InvestmentCatalogEntry("Silver", "Precious Metal")));
		lenient().when(investmentCatalogRepository.findById("Ethereum"))
				.thenReturn(Optional.of(new InvestmentCatalogEntry("Ethereum", "Cryptocurrency")));
	}

	@Test
	void createLooksUpTheCatalogAndSetsTypeAndWorth() {
		when(investmentRepository.save(any(Investment.class))).thenAnswer(invocation -> invocation.getArgument(0));

		InvestmentResponse response = investmentService
				.create(new CreateInvestmentRequest("Gold", new BigDecimal("5")));

		assertThat(response.name()).isEqualTo("Gold");
		assertThat(response.amount()).isEqualByComparingTo("5");
		assertThat(response.investmentType()).isEqualTo("Precious Metal");
		assertThat(response.worth()).isEqualByComparingTo("1");
	}

	@Test
	void createIgnoresAnyClientSuppliedTypeOrWorth() {
		// CreateInvestmentRequest only has name and amount, so this is enforced by the API shape itself;
		// this test documents that create() never reads type/worth from anywhere but the catalog
		when(investmentRepository.save(any(Investment.class))).thenAnswer(invocation -> invocation.getArgument(0));

		InvestmentResponse response = investmentService
				.create(new CreateInvestmentRequest("Ethereum", new BigDecimal("2")));

		assertThat(response.investmentType()).isEqualTo("Cryptocurrency");
		assertThat(response.worth()).isEqualByComparingTo("1");
	}

	@Test
	void createThrowsForAnUnknownName() {
		when(investmentCatalogRepository.findById("Doge")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> investmentService.create(new CreateInvestmentRequest("Doge", BigDecimal.ONE)))
				.isInstanceOf(UnknownInvestmentNameException.class)
				.hasMessageContaining("Doge");
	}

	@Test
	void findAllMapsEntitiesToResponses() {
		when(investmentRepository.findAll()).thenReturn(
				List.of(new Investment("Gold", new BigDecimal("5")), new Investment("Ethereum", new BigDecimal("3.5"))));

		List<InvestmentResponse> responses = investmentService.findAll();

		assertThat(responses).extracting(InvestmentResponse::name).containsExactly("Gold", "Ethereum");
	}

	@Test
	void findByIdReturnsInvestment() {
		UUID id = UUID.randomUUID();
		when(investmentRepository.findById(id)).thenReturn(Optional.of(new Investment("Gold", new BigDecimal("5"))));

		assertThat(investmentService.findById(id).name()).isEqualTo("Gold");
	}

	@Test
	void findByIdThrowsWhenMissing() {
		UUID id = UUID.randomUUID();
		when(investmentRepository.findById(id)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> investmentService.findById(id)).isInstanceOf(InvestmentNotFoundException.class)
				.hasMessageContaining(id.toString());
	}

	@Test
	void updateChangesNameAmountTypeAndWorth() {
		UUID id = UUID.randomUUID();
		Investment existing = new Investment("Gold", new BigDecimal("5"));
		when(investmentRepository.findById(id)).thenReturn(Optional.of(existing));

		InvestmentResponse response = investmentService
				.update(id, new UpdateInvestmentRequest("Silver", new BigDecimal("12.5")));

		assertThat(response.name()).isEqualTo("Silver");
		assertThat(response.amount()).isEqualByComparingTo("12.5");
		assertThat(response.investmentType()).isEqualTo("Precious Metal");
		assertThat(response.worth()).isEqualByComparingTo("1");
		assertThat(existing.getName()).isEqualTo("Silver");
	}

	@Test
	void updateThrowsForAnUnknownName() {
		UUID id = UUID.randomUUID();
		when(investmentCatalogRepository.findById("Doge")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> investmentService.update(id, new UpdateInvestmentRequest("Doge", BigDecimal.ONE)))
				.isInstanceOf(UnknownInvestmentNameException.class);
	}

	@Test
	void updateThrowsWhenMissing() {
		UUID id = UUID.randomUUID();
		when(investmentRepository.findById(id)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> investmentService.update(id, new UpdateInvestmentRequest("Silver", BigDecimal.ONE)))
				.isInstanceOf(InvestmentNotFoundException.class);
	}

	@Test
	void deleteRemovesExistingInvestment() {
		UUID id = UUID.randomUUID();
		Investment existing = new Investment("Gold", new BigDecimal("5"));
		when(investmentRepository.findById(id)).thenReturn(Optional.of(existing));

		investmentService.delete(id);

		verify(investmentRepository).delete(existing);
	}

	@Test
	void deleteThrowsWhenMissing() {
		UUID id = UUID.randomUUID();
		when(investmentRepository.findById(id)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> investmentService.delete(id)).isInstanceOf(InvestmentNotFoundException.class);
		verify(investmentRepository, never()).delete(any());
	}

}
