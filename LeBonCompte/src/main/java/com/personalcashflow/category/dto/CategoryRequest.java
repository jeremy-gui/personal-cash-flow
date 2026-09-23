package com.personalcashflow.category.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CategoryRequest(
        @NotBlank String name,
        Long parentId,
        Long defaultTransferAccountId,
        @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "must be a hex color like #2563eb") String color) {
}
