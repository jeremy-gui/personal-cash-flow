package com.personalcashflow.budget.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record BudgetRequest(
        @NotNull Long categoryId,
        @NotBlank @Pattern(regexp = "\\d{4}-\\d{2}", message = "must be in YYYY-MM format") String month,
        @NotNull @Positive BigDecimal amount) {
}
