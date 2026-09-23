package com.personalcashflow.budget.dto;

import com.personalcashflow.budget.Budget;
import java.math.BigDecimal;
import java.math.RoundingMode;

public record BudgetResponse(
        Long id,
        Long categoryId,
        String categoryName,
        String month,
        BigDecimal amount,
        BigDecimal spent,
        BigDecimal remaining,
        BigDecimal percentageConsumed) {

    public static BudgetResponse of(Budget budget, BigDecimal spent) {
        BigDecimal remaining = budget.getAmount().subtract(spent);
        BigDecimal percentage = spent
                .multiply(BigDecimal.valueOf(100))
                .divide(budget.getAmount(), 1, RoundingMode.HALF_UP);
        return new BudgetResponse(
                budget.getId(),
                budget.getCategory().getId(),
                budget.getCategory().getName(),
                budget.getMonth().toString().substring(0, 7),
                budget.getAmount(),
                spent,
                remaining,
                percentage);
    }
}
