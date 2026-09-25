package com.personalcashflow.recurring;

import com.personalcashflow.category.Category;
import com.personalcashflow.transaction.TransactionType;
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
@Table(name = "recurring_transaction")
public class RecurringTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String label;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType type;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    /** Always the first day of the month this recurrence was created in. */
    @Column(name = "start_month", nullable = false)
    private LocalDate startMonth;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RecurringTransaction() {
    }

    public RecurringTransaction(String label, BigDecimal amount, TransactionType type, Category category, LocalDate startMonth) {
        this.label = label;
        this.amount = amount;
        this.type = type;
        this.category = category;
        this.startMonth = startMonth;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public TransactionType getType() {
        return type;
    }

    public Category getCategory() {
        return category;
    }

    public LocalDate getStartMonth() {
        return startMonth;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Positive magnitude, like the constructor's; the sign still comes from {@code type}. */
    public void changeAmount(BigDecimal amount) {
        this.amount = amount;
    }

    /** Everything but the start month, which stays as created. */
    public void update(String label, BigDecimal amount, TransactionType type, Category category) {
        this.label = label;
        this.amount = amount;
        this.type = type;
        this.category = category;
    }
}
