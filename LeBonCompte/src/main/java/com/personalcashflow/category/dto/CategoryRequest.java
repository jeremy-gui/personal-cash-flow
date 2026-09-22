package com.personalcashflow.category.dto;

import jakarta.validation.constraints.NotBlank;

public record CategoryRequest(@NotBlank String name, Long parentId, Long defaultTransferAccountId) {
}
