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

    @Column(nullable = false, length = 7)
    private String color;

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

    /** Color-less: only for tests that never persist the category. */
    public Category(String name, Category parent) {
        this(name, parent, null, null);
    }

    public Category(String name, Category parent, Account defaultTransferAccount, String color) {
        this.name = name;
        this.parent = parent;
        this.defaultTransferAccount = defaultTransferAccount;
        this.color = color;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getColor() {
        return color;
    }

    public Category getParent() {
        return parent;
    }

    public Account getDefaultTransferAccount() {
        return defaultTransferAccount;
    }

    public void update(String name, Category parent, Account defaultTransferAccount, String color) {
        this.name = name;
        this.parent = parent;
        this.defaultTransferAccount = defaultTransferAccount;
        this.color = color;
    }

    /** Used to cascade a root's color down to its descendants, without touching their other fields. */
    void applyColor(String color) {
        this.color = color;
    }
}
