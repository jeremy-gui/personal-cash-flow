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

    /** The other account in a TRANSFERT pair; null for DEPENSE/REVENU. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transfer_account_id")
    private Account transferAccount;

    /**
     * The mirror row on {@link #transferAccount} (opposite sign, same date/label/category/status),
     * kept in sync by {@link com.personalcashflow.transaction.TransactionService}. Each side of a
     * pair simply points at the other's id - this is not a bidirectional JPA relationship.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "linked_transaction_id")
    private Transaction linkedTransaction;

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

    /**
     * False only while {@link #category} was auto-suggested from import history and hasn't been
     * reviewed yet - see {@link #suggestCategory} and {@link #confirmCategory}. Defaults true so a
     * manual/recurring/reconciled category is never mistaken for a lingering suggestion.
     */
    @Column(name = "category_confirmed", nullable = false)
    private boolean categoryConfirmed = true;

    /**
     * False only while {@link #label} was auto-suggested from import history and hasn't been
     * reviewed yet - see {@link #suggestLabel} and {@link #confirmLabel}. Defaults true so a
     * manual/reconciled label is never mistaken for a lingering suggestion.
     */
    @Column(name = "label_confirmed", nullable = false)
    private boolean labelConfirmed = true;

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

    /** Same as {@link #manual}, with the other account of a TRANSFERT pair attached. */
    public static Transaction manual(
            LocalDate date,
            String label,
            BigDecimal amount,
            Account account,
            Category category,
            TransactionType type,
            Account transferAccount) {
        Transaction transaction = manual(date, label, amount, account, category, type);
        transaction.transferAccount = transferAccount;
        return transaction;
    }

    /**
     * The other leg of a transfer, auto-generated to mirror its counterpart: opposite sign, same
     * date/label/category, and the same status (a POINTEE source produces a POINTEE mirror
     * immediately - a deliberate, narrow exception to "POINTEE only via import/reconciliation",
     * since the money really did move the moment the source side was pointée).
     */
    public static Transaction mirrorOf(Transaction source) {
        Transaction mirror = new Transaction(
                source.date,
                source.label,
                source.amount.negate(),
                source.transferAccount,
                source.category,
                TransactionType.TRANSFERT,
                source.status);
        mirror.source = TransactionSource.TRANSFER;
        mirror.transferAccount = source.account;
        return mirror;
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

    public Account getTransferAccount() {
        return transferAccount;
    }

    public Transaction getLinkedTransaction() {
        return linkedTransaction;
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

    public boolean isCategoryConfirmed() {
        return categoryConfirmed;
    }

    public boolean isLabelConfirmed() {
        return labelConfirmed;
    }

    /** Fills in the category from a reconciled counterpart, but never overwrites one already set. */
    public void assignCategoryIfMissing(Category category) {
        if (this.category == null) {
            this.category = category;
            this.categoryConfirmed = true;
        }
    }

    /** Fills in a category guessed from this account's import history - never overwrites one already set. */
    public void suggestCategory(Category category) {
        if (this.category == null) {
            this.category = category;
            this.categoryConfirmed = false;
        }
    }

    /** Marks a suggested category as reviewed and accepted. */
    public void confirmCategory() {
        this.categoryConfirmed = true;
    }

    /**
     * Reconciliation always prefers the previsionnelle's label (usually hand-written and more
     * meaningful) over the pointee's raw bank wording. The pointee's original label is not lost:
     * it stays available via {@link #getOriginalLabel}.
     */
    public void assignLabel(String label) {
        this.label = label;
        this.labelConfirmed = true;
    }

    /**
     * Fills in a friendlier label guessed from this account's import history - never overwrites a
     * label already customized away from the bank's raw wording (by the user or an earlier suggestion).
     */
    public void suggestLabel(String label) {
        if (this.label.equals(this.originalLabel) && !label.equals(this.label)) {
            this.label = label;
            this.labelConfirmed = false;
        }
    }

    /** Marks a suggested label as reviewed and accepted. */
    public void confirmLabel() {
        this.labelConfirmed = true;
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
            TransactionType type,
            Account transferAccount) {
        this.date = date;
        this.label = label;
        this.amount = amount;
        this.account = account;
        this.category = category;
        this.type = type;
        this.transferAccount = transferAccount;
        // A deliberate edit always counts as review, whether it keeps a suggested value or changes it.
        this.categoryConfirmed = true;
        this.labelConfirmed = true;
    }

    /** Points this row at its counterpart on the other side of a transfer. */
    public void linkTo(Transaction linkedTransaction) {
        this.linkedTransaction = linkedTransaction;
    }

    /** Drops the link to a counterpart, e.g. before deleting it or once this is no longer a transfer. */
    public void unlink() {
        this.linkedTransaction = null;
    }

    /** Keeps an existing mirror in sync after its counterpart ({@code source}) was edited. */
    public void mirrorFrom(Transaction source) {
        this.date = source.date;
        this.label = source.label;
        this.amount = source.amount.negate();
        this.account = source.transferAccount;
        this.category = source.category;
        this.status = source.status;
        this.transferAccount = source.account;
    }
}
