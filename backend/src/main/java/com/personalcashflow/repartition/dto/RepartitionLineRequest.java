package com.personalcashflow.repartition.dto;

import com.personalcashflow.repartition.RepartitionLineKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record RepartitionLineRequest(
        @NotNull RepartitionLineKind kind,
        @NotBlank String label,
        @NotNull @PositiveOrZero BigDecimal amount) {
}
