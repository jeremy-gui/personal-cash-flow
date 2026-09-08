package com.personalcashflow.budget;

import com.personalcashflow.common.NotFoundException;

public class BudgetNotFoundException extends NotFoundException {

    public BudgetNotFoundException(Long id) {
        super("Budget not found: " + id);
    }
}
