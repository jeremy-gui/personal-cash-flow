package com.personalcashflow.provision.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;

/** {@code active} defaults to true when omitted, so a minimal request never silently creates an inactive provision. */
public record ProvisionRequest(
        @NotBlank String name,
        @NotNull @Positive BigDecimal targetAmount,
        LocalDate targetDate,
        Long categoryId,
        Long accountId,
        Boolean active) {

    public boolean activeOrDefault() {
        return active == null || active;
    }
}
