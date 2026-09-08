package com.personalcashflow.transaction;

import com.personalcashflow.common.NotFoundException;

public class TransactionNotFoundException extends NotFoundException {

    public TransactionNotFoundException(Long id) {
        super("Transaction not found: " + id);
    }
}
