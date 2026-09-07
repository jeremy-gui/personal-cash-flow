package com.personalcashflow.account;

import com.personalcashflow.common.NotFoundException;

public class AccountNotFoundException extends NotFoundException {

    public AccountNotFoundException(Long id) {
        super("Account not found: " + id);
    }
}
