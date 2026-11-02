package com.personalcashflow.category;

import com.personalcashflow.common.NotFoundException;

public class CategoryNotFoundException extends NotFoundException {

    public CategoryNotFoundException(Long id) {
        super("Category not found: " + id);
    }
}
