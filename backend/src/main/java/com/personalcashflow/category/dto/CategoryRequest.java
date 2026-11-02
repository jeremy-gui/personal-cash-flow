package com.personalcashflow.category.dto;

import com.personalcashflow.transaction.TransactionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.List;

public record CategoryRequest(
        @NotBlank String name,
        Long parentId,
        Long defaultTransferAccountId,
        @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "must be a hex color like #2563eb") String color,
        /** Types allowed for hand-entered transactions (null/empty = any); only kept on a root category - sub-categories inherit it. */
        List<TransactionType> allowedTypes) {
}
