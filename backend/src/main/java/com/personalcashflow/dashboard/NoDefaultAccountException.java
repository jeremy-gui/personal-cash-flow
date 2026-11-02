package com.personalcashflow.dashboard;

import com.personalcashflow.common.BadRequestException;

public class NoDefaultAccountException extends BadRequestException {

    public NoDefaultAccountException() {
        super("No default account is configured");
    }
}
