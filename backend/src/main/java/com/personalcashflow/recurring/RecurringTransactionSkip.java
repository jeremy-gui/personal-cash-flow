package com.personalcashflow.recurring;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;

/** Marks a (recurring transaction, month) pair whose generated instance was explicitly deleted, so it is never regenerated. */
@Entity
@Table(name = "recurring_transaction_skip")
public class RecurringTransactionSkip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recurring_transaction_id", nullable = false)
    private RecurringTransaction recurringTransaction;

    /** Always the first day of the skipped month. */
    @Column(nullable = false)
    private LocalDate month;

    protected RecurringTransactionSkip() {
    }

    public RecurringTransactionSkip(RecurringTransaction recurringTransaction, LocalDate month) {
        this.recurringTransaction = recurringTransaction;
        this.month = month;
    }

    public Long getId() {
        return id;
    }

    public RecurringTransaction getRecurringTransaction() {
        return recurringTransaction;
    }

    public LocalDate getMonth() {
        return month;
    }
}
