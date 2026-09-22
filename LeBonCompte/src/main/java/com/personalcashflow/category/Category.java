package com.personalcashflow.category;

import com.personalcashflow.account.Account;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "category")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;

    /**
     * Pre-fills a transaction's target account when this category is picked and TRANSFERT is
     * chosen - a suggestion only, never enforced (see TransactionService).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "default_transfer_account_id")
    private Account defaultTransferAccount;

    protected Category() {
    }

    public Category(String name, Category parent) {
        this.name = name;
        this.parent = parent;
    }

    public Category(String name, Category parent, Account defaultTransferAccount) {
        this.name = name;
        this.parent = parent;
        this.defaultTransferAccount = defaultTransferAccount;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Category getParent() {
        return parent;
    }

    public Account getDefaultTransferAccount() {
        return defaultTransferAccount;
    }

    public void update(String name, Category parent, Account defaultTransferAccount) {
        this.name = name;
        this.parent = parent;
        this.defaultTransferAccount = defaultTransferAccount;
    }
}
