package com.personalcashflow.reconciliation;

import com.personalcashflow.common.BadRequestException;

public class InvalidMatchException extends BadRequestException {

    public InvalidMatchException(String message) {
        super(message);
    }
}
