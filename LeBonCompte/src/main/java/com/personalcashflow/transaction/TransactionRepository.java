package com.personalcashflow.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository
        extends JpaRepository<Transaction, Long>, JpaSpecificationExecutor<Transaction> {

    /** Sum of DEPENSE amounts (negative, or zero) for a category within a date range. */
    @Query("""
            SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t
            WHERE t.category.id = :categoryId
              AND t.type = com.personalcashflow.transaction.TransactionType.DEPENSE
              AND t.date >= :from
              AND t.date <= :to
            """)
    BigDecimal sumDepenseByCategoryAndDateRange(
            @Param("categoryId") Long categoryId, @Param("from") LocalDate from, @Param("to") LocalDate to);
}
