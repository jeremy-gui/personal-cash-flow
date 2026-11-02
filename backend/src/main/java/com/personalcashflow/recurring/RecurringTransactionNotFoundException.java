package com.personalcashflow.recurring;

import com.personalcashflow.common.NotFoundException;

public class RecurringTransactionNotFoundException extends NotFoundException {

    public RecurringTransactionNotFoundException(Long id) {
        super("Recurring transaction not found: " + id);
    }
}
