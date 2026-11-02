package com.personalcashflow.budget;

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

@Entity
@Table(name = "budget")
public class Budget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** One budget row per (category, month): a category can have a different amount per month. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    /** Always the first day of the month this row's amount applies to. */
    @Column(nullable = false)
    private LocalDate month;

    @Column(nullable = false)
    private BigDecimal amount;

    protected Budget() {
    }

    public Budget(Category category, LocalDate month, BigDecimal amount) {
        this.category = category;
        this.month = month;
        this.amount = amount;
    }

    public Long getId() {
        return id;
    }

    public Category getCategory() {
        return category;
    }

    public LocalDate getMonth() {
        return month;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    /** The month never changes after creation: only the amount/category can be edited. */
    public void update(Category category, BigDecimal amount) {
        this.category = category;
        this.amount = amount;
    }
}
