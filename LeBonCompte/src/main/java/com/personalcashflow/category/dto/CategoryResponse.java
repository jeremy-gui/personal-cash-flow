package com.personalcashflow.category.dto;

import com.personalcashflow.category.Category;

public record CategoryResponse(
        Long id, String name, Long parentId, Long defaultTransferAccountId, String defaultTransferAccountName) {

    public static CategoryResponse from(Category category) {
        Long parentId = category.getParent() != null ? category.getParent().getId() : null;
        var transferAccount = category.getDefaultTransferAccount();
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                parentId,
                transferAccount != null ? transferAccount.getId() : null,
                transferAccount != null ? transferAccount.getName() : null);
    }
}
