package com.investmenttracker.investmentservice.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InvestmentCatalogRepository extends JpaRepository<InvestmentCatalogEntry, String> {

}
