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
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "budget", uniqueConstraints = @UniqueConstraint(columnNames = {"category_id", "month"}))
public class Budget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    /** Always the first day of the budgeted month. */
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

    public void update(Category category, LocalDate month, BigDecimal amount) {
        this.category = category;
        this.month = month;
        this.amount = amount;
    }
}
