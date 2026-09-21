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

    /** Sum of DEPENSE amounts (negative, or zero) for a category within a date range. Household-wide (budgets aren't per-account). */
    @Query("""
            SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t
            WHERE t.category.id = :categoryId
              AND t.type = com.personalcashflow.transaction.TransactionType.DEPENSE
              AND t.date >= :from
              AND t.date <= :to
            """)
    BigDecimal sumDepenseByCategoryAndDateRange(
            @Param("categoryId") Long categoryId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Sum of amounts for a transaction type within a date range, for one account (negative for DEPENSE, positive for REVENU). */
    @Query("""
            SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t
            WHERE t.type = :type
              AND t.account.id = :accountId
              AND t.date >= :from
              AND t.date <= :to
            """)
    BigDecimal sumByTypeAndDateRange(
            @Param("type") TransactionType type,
            @Param("accountId") Long accountId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /** DEPENSE totals (negative, or zero) grouped by category within a date range, for one account. Uncategorized transactions are excluded. */
    @Query("""
            SELECT c.id AS categoryId, c.name AS categoryName, SUM(t.amount) AS total
            FROM Transaction t JOIN t.category c
            WHERE t.type = com.personalcashflow.transaction.TransactionType.DEPENSE
              AND t.account.id = :accountId
              AND t.date >= :from
              AND t.date <= :to
            GROUP BY c.id, c.name
            ORDER BY SUM(t.amount) ASC
            """)
    List<CategorySpendingProjection> sumDepenseGroupedByCategory(
            @Param("accountId") Long accountId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    boolean existsByAccountIdAndImportHash(Long accountId, String importHash);

    /**
     * Catches near-duplicates the import hash misses - e.g. the same real transaction imported once
     * via OFX and once via CSV, whose hashes have nothing in common - by amount + original date, with
     * either original label being a prefix of the other (OFX and CSV truncate the bank's own wording
     * differently). {@code label} must already have '%', '_' and '\' escaped by the caller: only that
     * side of the comparison can be escaped safely, the other compares against a stored column value.
     */
    @Query("""
            SELECT COUNT(t) > 0 FROM Transaction t
            WHERE t.account.id = :accountId
              AND t.amount = :amount
              AND t.originalDate = :originalDate
              AND (t.originalLabel LIKE CONCAT(:label, '%') ESCAPE '\\'
                   OR :label LIKE CONCAT(t.originalLabel, '%'))
            """)
    boolean existsSimilarByAccountAmountOriginalDateAndLabelPrefix(
            @Param("accountId") Long accountId,
            @Param("amount") BigDecimal amount,
            @Param("originalDate") LocalDate originalDate,
            @Param("label") String label);

    boolean existsByRecurringTransactionIdAndDate(Long recurringTransactionId, LocalDate date);

    /** Scoped to {@code importHash} prefix so this only ever returns Excel-backfilled placeholder rows. */
    List<Transaction> findByAccountIdAndDateBetweenAndStatusAndImportHashStartingWith(
            Long accountId, LocalDate from, LocalDate to, TransactionStatus status, String importHashPrefix);

    /** Sum of amounts for a status, for one account, across all dates (no month scoping). */
    @Query("""
            SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t
            WHERE t.status = :status
              AND t.account.id = :accountId
            """)
    BigDecimal sumByStatus(@Param("status") TransactionStatus status, @Param("accountId") Long accountId);

    /** Sum of amounts for a status, for one account, up to and including a given date. */
    @Query("""
            SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t
            WHERE t.status = :status
              AND t.account.id = :accountId
              AND t.date <= :upTo
            """)
    BigDecimal sumByStatusUpToDate(
            @Param("status") TransactionStatus status,
            @Param("accountId") Long accountId,
            @Param("upTo") LocalDate upTo);

    /** Every pointée's bank label, current label and category, for suggesting a category/label to newly imported rows of one account. */
    @Query("""
            SELECT t.originalLabel AS originalLabel, t.label AS label, t.category.id AS categoryId FROM Transaction t
            WHERE t.account.id = :accountId
              AND t.status = :status
              AND t.originalLabel IS NOT NULL
            """)
    List<LabelCategoryProjection> findSuggestionHistoryByAccountIdAndStatus(
            @Param("accountId") Long accountId, @Param("status") TransactionStatus status);
}
