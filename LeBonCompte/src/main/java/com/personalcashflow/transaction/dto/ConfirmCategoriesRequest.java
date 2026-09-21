package com.personalcashflow.transaction.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ConfirmCategoriesRequest(@NotEmpty List<@NotNull Long> ids) {
}
