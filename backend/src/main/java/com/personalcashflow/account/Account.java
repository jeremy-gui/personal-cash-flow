package com.personalcashflow.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "account")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountType type;

    @Column(name = "initial_balance", nullable = false)
    private BigDecimal initialBalance;

    @Column(name = "account_number")
    private String accountNumber;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Account() {
    }

    public Account(String name, AccountType type, BigDecimal initialBalance, String accountNumber) {
        this.name = name;
        this.type = type;
        this.initialBalance = initialBalance;
        this.accountNumber = accountNumber;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public AccountType getType() {
        return type;
    }

    public BigDecimal getInitialBalance() {
        return initialBalance;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void update(String name, AccountType type, BigDecimal initialBalance, String accountNumber) {
        this.name = name;
        this.type = type;
        this.initialBalance = initialBalance;
        this.accountNumber = accountNumber;
    }

    public void markDefault() {
        this.isDefault = true;
    }

    public void unmarkDefault() {
        this.isDefault = false;
    }
}
