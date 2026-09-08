package com.personalcashflow.category;

import com.personalcashflow.common.ConflictException;

public class CategoryHasChildrenException extends ConflictException {

    public CategoryHasChildrenException(Long id) {
        super("Category " + id + " has child categories and cannot be deleted");
    }
}
