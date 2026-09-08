package com.personalcashflow.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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

    /** Sum of amounts for a transaction type within a date range (negative for DEPENSE, positive for REVENU). */
    @Query("""
            SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t
            WHERE t.type = :type
              AND t.date >= :from
              AND t.date <= :to
            """)
    BigDecimal sumByTypeAndDateRange(
            @Param("type") TransactionType type, @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** DEPENSE totals (negative, or zero) grouped by category within a date range. Uncategorized transactions are excluded. */
    @Query("""
            SELECT c.id AS categoryId, c.name AS categoryName, SUM(t.amount) AS total
            FROM Transaction t JOIN t.category c
            WHERE t.type = com.personalcashflow.transaction.TransactionType.DEPENSE
              AND t.date >= :from
              AND t.date <= :to
            GROUP BY c.id, c.name
            ORDER BY SUM(t.amount) ASC
            """)
    List<CategorySpendingProjection> sumDepenseGroupedByCategory(
            @Param("from") LocalDate from, @Param("to") LocalDate to);

    boolean existsByAccountIdAndImportHash(Long accountId, String importHash);

    /** Sum of amounts for a status, across all accounts and all dates (no month scoping). */
    @Query("""
            SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t
            WHERE t.status = :status
            """)
    BigDecimal sumByStatus(@Param("status") TransactionStatus status);

    /** Sum of amounts for a status, across all accounts, up to and including a given date. */
    @Query("""
            SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t
            WHERE t.status = :status
              AND t.date <= :upTo
            """)
    BigDecimal sumByStatusUpToDate(@Param("status") TransactionStatus status, @Param("upTo") LocalDate upTo);
}
