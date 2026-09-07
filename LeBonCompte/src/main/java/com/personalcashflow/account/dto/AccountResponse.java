package com.personalcashflow.account.dto;

import com.personalcashflow.account.Account;
import com.personalcashflow.account.AccountType;
import java.math.BigDecimal;

public record AccountResponse(
        Long id,
        String name,
        AccountType type,
        BigDecimal initialBalance) {

    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getName(),
                account.getType(),
                account.getInitialBalance());
    }
}
