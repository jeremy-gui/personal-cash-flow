package com.personalcashflow.reconciliation.dto;

import jakarta.validation.constraints.NotNull;

public record MatchRequest(@NotNull Long matchWithId) {
}
