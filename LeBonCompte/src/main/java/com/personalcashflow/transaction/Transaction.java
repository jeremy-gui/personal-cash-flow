package com.personalcashflow.transaction;

import com.personalcashflow.account.Account;
import com.personalcashflow.category.Category;
import com.personalcashflow.recurring.RecurringTransaction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "transaction")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private String label;

    @Column(nullable = false)
    private BigDecimal amount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionSource source;

    /** The bank's own unique transaction id (OFX FITID) for imported rows; null for manual entries. */
    @Column(name = "import_hash")
    private String importHash;

    /** A summary of the original OFX transaction record, preserved when the transaction came from an import. */
    @Column(name = "raw_import_data")
    private String rawImportData;

    /**
     * The bank's own date/label at import time, set once by {@link #imported} and never touched
     * again by {@link #update} or {@link #assignLabel}. Null for manual entries, which have no
     * bank origin to diverge from.
     */
    @Column(name = "original_date")
    private LocalDate originalDate;

    @Column(name = "original_label")
    private String originalLabel;

    /** The recurring definition this instance was generated from, if any; never set for manual/imported rows. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recurring_transaction_id")
    private RecurringTransaction recurringTransaction;

    /** True once this pointee has absorbed a previsionnelle via reconciliation - see {@link #markMatched}. */
    @Column(nullable = false)
    private boolean matched;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Transaction() {
    }

    public Transaction(
            LocalDate date,
            String label,
            BigDecimal amount,
            Account account,
            Category category,
            TransactionType type,
            TransactionStatus status) {
        this.date = date;
        this.label = label;
        this.amount = amount;
        this.account = account;
        this.category = category;
        this.type = type;
        this.status = status;
        this.source = TransactionSource.MANUAL;
        this.createdAt = Instant.now();
    }

    /** Manual entries are always PREVISIONNELLE; only a bank import or a reconciliation produces POINTEE. */
    public static Transaction manual(
            LocalDate date,
            String label,
            BigDecimal amount,
            Account account,
            Category category,
            TransactionType type) {
        return new Transaction(date, label, amount, account, category, type, TransactionStatus.PREVISIONNELLE);
    }

    public static Transaction imported(
            LocalDate date,
            String label,
            BigDecimal amount,
            Account account,
            TransactionType type,
            String importHash,
            String rawImportData) {
        Transaction transaction = new Transaction(date, label, amount, account, null, type, TransactionStatus.POINTEE);
        transaction.source = TransactionSource.IMPORT;
        transaction.importHash = importHash;
        transaction.rawImportData = rawImportData;
        transaction.originalDate = date;
        transaction.originalLabel = label;
        return transaction;
    }

    /** Generated from a recurring definition; always PREVISIONNELLE, same as a manual entry. */
    public static Transaction recurring(
            LocalDate date,
            String label,
            BigDecimal amount,
            Account account,
            Category category,
            TransactionType type,
            RecurringTransaction recurringTransaction) {
        Transaction transaction = new Transaction(date, label, amount, account, category, type, TransactionStatus.PREVISIONNELLE);
        transaction.recurringTransaction = recurringTransaction;
        return transaction;
    }

    public Long getId() {
        return id;
    }

    public LocalDate getDate() {
        return date;
    }

    public String getLabel() {
        return label;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Account getAccount() {
        return account;
    }

    public Category getCategory() {
        return category;
    }

    public TransactionType getType() {
        return type;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public TransactionSource getSource() {
        return source;
    }

    public String getImportHash() {
        return importHash;
    }

    public String getRawImportData() {
        return rawImportData;
    }

    public LocalDate getOriginalDate() {
        return originalDate;
    }

    public String getOriginalLabel() {
        return originalLabel;
    }

    public RecurringTransaction getRecurringTransaction() {
        return recurringTransaction;
    }

    public boolean isMatched() {
        return matched;
    }

    /** Fills in the category from a reconciled counterpart, but never overwrites one already set. */
    public void assignCategoryIfMissing(Category category) {
        if (this.category == null) {
            this.category = category;
        }
    }

    /**
     * Reconciliation always prefers the previsionnelle's label (usually hand-written and more
     * meaningful) over the pointee's raw bank wording. The pointee's original label is not lost:
     * it stays available via {@link #getOriginalLabel}.
     */
    public void assignLabel(String label) {
        this.label = label;
    }

    /** Marks this pointee as having absorbed a previsionnelle, so it stops being offered as a rapprochement candidate. */
    public void markMatched() {
        this.matched = true;
    }

    /**
     * Attaches the bank's real original data discovered later from a matching OFX row (e.g. a
     * history backfilled from a spreadsheet, later reconciled against a real bank export). Never
     * touches {@code date}/{@code label}/{@code amount} - those are the record everyone already
     * sees and reasons about.
     */
    public void enrichOriginal(LocalDate originalDate, String originalLabel, String importHash, String rawImportData) {
        this.originalDate = originalDate;
        this.originalLabel = originalLabel;
        this.importHash = importHash;
        this.rawImportData = rawImportData;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Status is never editable here: it only changes via a bank import or a reconciliation. */
    public void update(
            LocalDate date,
            String label,
            BigDecimal amount,
            Account account,
            Category category,
            TransactionType type) {
        this.date = date;
        this.label = label;
        this.amount = amount;
        this.account = account;
        this.category = category;
        this.type = type;
    }
}
