package com.personalcashflow.category;

import com.personalcashflow.account.Account;
import com.personalcashflow.transaction.TransactionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

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

    /**
     * Types a hand-entered transaction of this category (and its sub-categories) may take, stored
     * comma-separated in DEPENSE, REVENU, TRANSFERT order (e.g. "REVENU,TRANSFERT"); null = any.
     * Only set on a root category; enforced by TransactionService. Never applied to imported rows,
     * whose type always comes from the bank amount's sign.
     */
    @Column(name = "allowed_types", length = 60)
    private String allowedTypes;

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

    /** Empty when every type is allowed. */
    public List<TransactionType> getAllowedTypes() {
        return allowedTypes == null
                ? List.of()
                : Arrays.stream(allowedTypes.split(",")).map(TransactionType::valueOf).toList();
    }

    /** Null or empty means any type; always stored in DEPENSE, REVENU, TRANSFERT order. */
    public void changeAllowedTypes(Collection<TransactionType> types) {
        this.allowedTypes = types == null || types.isEmpty()
                ? null
                : Arrays.stream(TransactionType.values()).filter(types::contains).map(Enum::name)
                        .collect(Collectors.joining(","));
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
