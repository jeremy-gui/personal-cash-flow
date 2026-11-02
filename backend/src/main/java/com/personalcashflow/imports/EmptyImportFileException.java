package com.personalcashflow.imports;

import com.personalcashflow.common.BadRequestException;

public class EmptyImportFileException extends BadRequestException {

    public EmptyImportFileException() {
        super("The uploaded file is empty");
    }
}
