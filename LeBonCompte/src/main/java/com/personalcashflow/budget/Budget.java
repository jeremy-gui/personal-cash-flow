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

    /** One budget per category: creating a second one for the same category is a conflict. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false, unique = true)
    private Category category;

    /** Always the first day of the month this budget started applying from. */
    @Column(name = "start_month", nullable = false)
    private LocalDate startMonth;

    @Column(nullable = false)
    private BigDecimal amount;

    protected Budget() {
    }

    public Budget(Category category, LocalDate startMonth, BigDecimal amount) {
        this.category = category;
        this.startMonth = startMonth;
        this.amount = amount;
    }

    public Long getId() {
        return id;
    }

    public Category getCategory() {
        return category;
    }

    public LocalDate getStartMonth() {
        return startMonth;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    /** The start month never changes after creation: only the amount/category can be edited. */
    public void update(Category category, BigDecimal amount) {
        this.category = category;
        this.amount = amount;
    }
}
