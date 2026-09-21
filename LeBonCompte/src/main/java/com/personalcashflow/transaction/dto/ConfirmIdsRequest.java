package com.personalcashflow.transaction.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Shared shape for the bulk confirm-categories and confirm-labels endpoints. */
public record ConfirmIdsRequest(@NotEmpty List<@NotNull Long> ids) {
}
