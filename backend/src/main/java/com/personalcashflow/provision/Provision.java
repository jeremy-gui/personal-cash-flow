package com.personalcashflow.provision;

import com.personalcashflow.account.Account;
import com.personalcashflow.category.Category;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Money mentally set aside for a known future expense (e.g. taxe foncière). Deliberately not
 * tied to {@code Transaction}: a provision doesn't need a dedicated account and its contributions
 * shouldn't have to satisfy the transaction ledger's import/reconciliation rules - see
 * {@link ProvisionContribution}, an independent, manually-entered ledger.
 */
@Entity
@Table(name = "provision")
public class Provision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "target_amount", nullable = false)
    private BigDecimal targetAmount;

    @Column(name = "target_date")
    private LocalDate targetDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    /** Optional: a provision doesn't need a dedicated account, but can reference one. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    private Account account;

    @Column(nullable = false)
    private boolean active = true;

    protected Provision() {
    }

    public Provision(
            String name, BigDecimal targetAmount, LocalDate targetDate, Category category, Account account) {
        this.name = name;
        this.targetAmount = targetAmount;
        this.targetDate = targetDate;
        this.category = category;
        this.account = account;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getTargetAmount() {
        return targetAmount;
    }

    public LocalDate getTargetDate() {
        return targetDate;
    }

    public Category getCategory() {
        return category;
    }

    public Account getAccount() {
        return account;
    }

    public boolean isActive() {
        return active;
    }

    public void update(
            String name,
            BigDecimal targetAmount,
            LocalDate targetDate,
            Category category,
            Account account,
            boolean active) {
        this.name = name;
        this.targetAmount = targetAmount;
        this.targetDate = targetDate;
        this.category = category;
        this.account = account;
        this.active = active;
    }
}
