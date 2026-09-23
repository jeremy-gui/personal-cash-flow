package com.personalcashflow.provision.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ProvisionContributionRequest(@NotNull @Positive BigDecimal amount, @NotNull LocalDate date) {
}
