package com.personalcashflow.recurring.dto;

import com.personalcashflow.transaction.TransactionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/** {@code amount} is the positive magnitude; the sign is derived from {@code type}. */
public record RecurringTransactionRequest(
        @NotBlank String label,
        @NotNull @Positive BigDecimal amount,
        @NotNull TransactionType type,
        @NotNull Long categoryId,
        /** True when the amount varies month to month (EDF, courses...) - it is then only an estimate. */
        boolean variableAmount,
        /** TRANSFERT only: the other account (defaults to the category's compte cible). */
        Long transferAccountId,
        /** TRANSFERT only: true (default) = leaves the default account, false = arrives on it. */
        Boolean outgoing) {
}
