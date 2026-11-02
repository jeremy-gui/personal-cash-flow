package com.personalcashflow.transaction.dto;

import com.personalcashflow.transaction.TransactionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * {@code amount} is the positive magnitude. The sign is derived from {@code type} for
 * DEPENSE/REVENU; for TRANSFERT it's derived from {@code outgoing} instead (true = leaves this
 * account, false = arrives into it) since TRANSFERT alone doesn't imply a direction.
 * {@code transferAccountId} is required when {@code type} is TRANSFERT, ignored otherwise.
 *
 * <p>There is no {@code status}: manual entries are always PREVISIONNELLE. POINTEE only comes
 * from a bank import, a reconciliation, or the mirror side of a TRANSFERT.
 */
public record TransactionRequest(
        @NotNull LocalDate date,
        @NotBlank String label,
        @NotNull @Positive BigDecimal amount,
        @NotNull Long accountId,
        Long categoryId,
        @NotNull TransactionType type,
        Long transferAccountId,
        boolean outgoing) {
}
