package com.personalcashflow.imports;

import com.personalcashflow.common.BadRequestException;

public class UnmatchedOfxAccountException extends BadRequestException {

    public UnmatchedOfxAccountException(String accountNumber) {
        super("No account matches the OFX account number: " + (accountNumber == null ? "none" : accountNumber));
    }
}
