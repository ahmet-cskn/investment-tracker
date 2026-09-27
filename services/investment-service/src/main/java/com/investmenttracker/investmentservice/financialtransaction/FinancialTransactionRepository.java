package com.investmenttracker.investmentservice.financialtransaction;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancialTransactionRepository extends JpaRepository<FinancialTransaction, UUID> {

}
