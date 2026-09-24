package com.personalcashflow.recurring.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/** Positive magnitude; the sign is derived from the recurring transaction's type. */
public record RecurringAmountRequest(@NotNull @Positive BigDecimal amount) {
}
