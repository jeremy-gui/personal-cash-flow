package com.personalcashflow.category;

import com.personalcashflow.common.BadRequestException;

public class CategoryCycleException extends BadRequestException {

    public CategoryCycleException(Long id, Long parentId) {
        super("Category " + parentId + " cannot be the parent of " + id + ": it would create a cycle");
    }
}
