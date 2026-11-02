package com.personalcashflow.reconciliation.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record MatchRequest(@NotEmpty List<@NotNull Long> matchWithIds) {
}
