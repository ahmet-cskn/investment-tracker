package com.investmenttracker.investmentservice.transactionhistory;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionHistoryRepository extends JpaRepository<TransactionHistory, UUID> {

}
