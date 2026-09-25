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

    /**
     * Sum of DEPENSE amounts (negative, or zero) for a category and all its descendants within a
     * date range, so a budget on a parent covers its sub-categories. Household-wide (budgets aren't
     * per-account). Native because JPQL has no recursive query.
     */
    @Query(value = """
            WITH RECURSIVE tree AS (
                SELECT id FROM category WHERE id = :categoryId
                UNION ALL
                SELECT c.id FROM category c JOIN tree ON c.parent_id = tree.id
            )
            SELECT COALESCE(SUM(t.amount), 0) FROM transaction t
            WHERE t.category_id IN (SELECT id FROM tree)
              AND t.type = 'DEPENSE'
              AND t.date >= :from
              AND t.date <= :to
            """, nativeQuery = true)
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

    /** Sum of one direction of TRANSFERT amounts within a date range, for one account: incoming (positive) or outgoing (negative). */
    @Query("""
            SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t
            WHERE t.type = com.personalcashflow.transaction.TransactionType.TRANSFERT
              AND t.account.id = :accountId
              AND t.date >= :from
              AND t.date <= :to
              AND ((:incoming = true AND t.amount > 0) OR (:incoming = false AND t.amount < 0))
            """)
    BigDecimal sumTransfersByDirection(
            @Param("incoming") boolean incoming,
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

    /** Sum of amounts for a transaction type within a date range, across every account. */
    @Query("""
            SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t
            WHERE t.type = :type
              AND t.date >= :from
              AND t.date <= :to
            """)
    BigDecimal sumByTypeAndDateRangeAllAccounts(
            @Param("type") TransactionType type, @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** DEPENSE totals (negative, or zero) grouped by category within a date range, across every account. */
    @Query("""
            SELECT c.id AS categoryId, c.name AS categoryName, SUM(t.amount) AS total
            FROM Transaction t JOIN t.category c
            WHERE t.type = com.personalcashflow.transaction.TransactionType.DEPENSE
              AND t.date >= :from
              AND t.date <= :to
            GROUP BY c.id, c.name
            ORDER BY SUM(t.amount) ASC
            """)
    List<CategorySpendingProjection> sumDepenseGroupedByCategoryAllAccounts(
            @Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * Sum of TRANSFERT amounts landing on LIVRET-type accounts (positive legs only) - i.e. money
     * that actually moved into savings within the period, across every account. A LIVRET account
     * is itself the definition of "savings" here (see AccountType), except money in the "Provision"
     * category: it's set aside for a known upcoming expense (charges, taxe foncière), not saved.
     */
    @Query("""
            SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t LEFT JOIN t.category c
            WHERE t.type = com.personalcashflow.transaction.TransactionType.TRANSFERT
              AND t.account.type = com.personalcashflow.account.AccountType.LIVRET
              AND t.amount > 0
              AND t.date >= :from
              AND t.date <= :to
              AND (c IS NULL OR c.name <> 'Provision')
            """)
    BigDecimal sumSavingsContributions(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * Money taken out of LIVRET accounts within the period (outgoing transfers and dépenses paid
     * straight from a livret), as a negative sum - "Provision" transfers excluded, like in
     * {@link #sumSavingsContributions}.
     */
    @Query("""
            SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t LEFT JOIN t.category c
            WHERE t.type IN (com.personalcashflow.transaction.TransactionType.TRANSFERT,
                             com.personalcashflow.transaction.TransactionType.DEPENSE)
              AND t.account.type = com.personalcashflow.account.AccountType.LIVRET
              AND t.amount < 0
              AND t.date >= :from
              AND t.date <= :to
              AND (c IS NULL OR c.name <> 'Provision')
            """)
    BigDecimal sumSavingsWithdrawals(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Same as {@link #sumSavingsContributions}, broken down by which LIVRET account received the money. */
    @Query("""
            SELECT t.account.id AS accountId, t.account.name AS accountName, SUM(t.amount) AS total
            FROM Transaction t LEFT JOIN t.category c
            WHERE t.type = com.personalcashflow.transaction.TransactionType.TRANSFERT
              AND t.account.type = com.personalcashflow.account.AccountType.LIVRET
              AND t.amount > 0
              AND t.date >= :from
              AND t.date <= :to
              AND (c IS NULL OR c.name <> 'Provision')
            GROUP BY t.account.id, t.account.name
            ORDER BY SUM(t.amount) DESC
            """)
    List<AccountAmountProjection> sumSavingsContributionsGroupedByAccount(
            @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** DEPENSE totals (negative, or zero) grouped by category and calendar month within a date range, across every account. */
    @Query("""
            SELECT c.id AS categoryId, c.name AS categoryName, month(t.date) AS month, SUM(t.amount) AS total
            FROM Transaction t JOIN t.category c
            WHERE t.type = com.personalcashflow.transaction.TransactionType.DEPENSE
              AND t.date >= :from
              AND t.date <= :to
            GROUP BY c.id, c.name, month(t.date)
            ORDER BY month(t.date) ASC
            """)
    List<MonthlyCategorySpendingProjection> sumDepenseGroupedByMonthAndCategoryAllAccounts(
            @Param("from") LocalDate from, @Param("to") LocalDate to);

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

    /**
     * What one account really had to cover over a period, from its pointées: every outflow
     * (dépenses and outgoing transfers - épargne, provisions) minus every inflow that isn't one of
     * the funding sources the salary split already accounts for (Salaire, Allocations, Renflouage).
     * Reimbursements and money sent back from the livrets therefore lower the figure.
     */
    @Query("""
            SELECT COALESCE(SUM(CASE
                       WHEN t.amount < 0 THEN -t.amount
                       WHEN c IS NULL OR c.name NOT IN ('Salaire', 'Allocations', 'Renflouage') THEN -t.amount
                       ELSE 0 END), 0)
            FROM Transaction t LEFT JOIN t.category c
            WHERE t.account.id = :accountId
              AND t.status = com.personalcashflow.transaction.TransactionStatus.POINTEE
              AND t.date >= :from
              AND t.date <= :to
            """)
    BigDecimal sumNetFrais(@Param("accountId") Long accountId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    List<Transaction> findByRecurringTransactionIdAndStatusAndDateGreaterThanEqual(
            Long recurringTransactionId, TransactionStatus status, LocalDate from);

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
