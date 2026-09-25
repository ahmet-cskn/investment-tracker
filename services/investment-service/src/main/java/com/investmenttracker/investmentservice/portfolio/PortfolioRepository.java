package com.investmenttracker.investmentservice.portfolio;

import com.investmenttracker.investmentservice.investment.Investment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * Read-only aggregate queries over the investments (initial holdings) and transaction_history tables.
 * The sums are done in the database, on NUMERIC(38,18) values, so no precision is lost.
 */
public interface PortfolioRepository extends Repository<Investment, UUID> {

	@Query("select new com.investmenttracker.investmentservice.portfolio.NameTotal(i.name, sum(i.amount)) "
			+ "from Investment i group by i.name")
	List<NameTotal> sumInitialAmountsByName();

	@Query("select new com.investmenttracker.investmentservice.portfolio.NameTotal(t.name, sum(t.change)) "
			+ "from TransactionHistory t group by t.name")
	List<NameTotal> sumChangesByName();

}
