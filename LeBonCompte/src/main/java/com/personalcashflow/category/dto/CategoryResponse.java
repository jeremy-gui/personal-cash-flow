package com.personalcashflow.category.dto;

import com.personalcashflow.category.Category;
import com.personalcashflow.transaction.TransactionType;
import java.util.List;

public record CategoryResponse(
        Long id,
        String name,
        String color,
        Long parentId,
        Long defaultTransferAccountId,
        String defaultTransferAccountName,
        /** The category's own list (empty on sub-categories, which inherit their root's); empty root = any type. */
        List<TransactionType> allowedTypes) {

    public static CategoryResponse from(Category category) {
        Long parentId = category.getParent() != null ? category.getParent().getId() : null;
        var transferAccount = category.getDefaultTransferAccount();
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getColor(),
                parentId,
                transferAccount != null ? transferAccount.getId() : null,
                transferAccount != null ? transferAccount.getName() : null,
                category.getAllowedTypes());
    }
}
