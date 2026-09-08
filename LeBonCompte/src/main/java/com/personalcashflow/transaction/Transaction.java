package com.personalcashflow.transaction;

import com.personalcashflow.account.Account;
import com.personalcashflow.category.Category;
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

    /** Deterministic dedup key (date|label|amount) for imported rows; null for manual entries. */
    @Column(name = "import_hash")
    private String importHash;

    /** The original CSV line, preserved as-is when the transaction came from an import. */
    @Column(name = "raw_import_data")
    private String rawImportData;

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

    /** Fills in the category from a reconciled counterpart, but never overwrites one already set. */
    public void assignCategoryIfMissing(Category category) {
        if (this.category == null) {
            this.category = category;
        }
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void update(
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
    }
}
