package com.personalcashflow.category.dto;

import com.personalcashflow.category.Category;

public record CategoryResponse(Long id, String name, Long parentId) {

    public static CategoryResponse from(Category category) {
        Long parentId = category.getParent() != null ? category.getParent().getId() : null;
        return new CategoryResponse(category.getId(), category.getName(), parentId);
    }
}
