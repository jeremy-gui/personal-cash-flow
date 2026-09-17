package com.personalcashflow.account;

import com.personalcashflow.common.BadRequestException;

public class AccountNumberAlreadyUsedException extends BadRequestException {

    public AccountNumberAlreadyUsedException(String accountNumber) {
        super("Account number already in use: " + accountNumber);
    }
}
