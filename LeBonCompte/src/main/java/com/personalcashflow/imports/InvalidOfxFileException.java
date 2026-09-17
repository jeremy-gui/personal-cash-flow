package com.personalcashflow.imports;

import com.personalcashflow.common.BadRequestException;

public class InvalidOfxFileException extends BadRequestException {

    public InvalidOfxFileException(Throwable cause) {
        super("The uploaded file is not a valid OFX document: " + cause.getMessage());
        initCause(cause);
    }
}
