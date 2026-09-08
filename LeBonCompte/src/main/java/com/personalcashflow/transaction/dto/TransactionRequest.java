package com.personalcashflow.transaction.dto;

import com.personalcashflow.transaction.TransactionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * {@code amount} is the positive magnitude; the sign is derived from {@code type}.
 *
 * <p>There is no {@code status}: manual entries are always PREVISIONNELLE. POINTEE only comes
 * from a bank import or from reconciling a previsionnelle against one.
 */
public record TransactionRequest(
        @NotNull LocalDate date,
        @NotBlank String label,
        @NotNull @Positive BigDecimal amount,
        @NotNull Long accountId,
        Long categoryId,
        @NotNull TransactionType type) {
}
