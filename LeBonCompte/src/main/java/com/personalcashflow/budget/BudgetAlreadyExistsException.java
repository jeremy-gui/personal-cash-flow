package com.personalcashflow.budget;

import com.personalcashflow.common.ConflictException;

public class BudgetAlreadyExistsException extends ConflictException {

    public BudgetAlreadyExistsException(Long categoryId, String month) {
        super("A budget already exists for category " + categoryId + " in " + month);
    }
}
