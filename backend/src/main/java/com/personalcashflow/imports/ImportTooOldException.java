package com.personalcashflow.imports;

import com.personalcashflow.common.BadRequestException;
import java.time.LocalDate;

public class ImportTooOldException extends BadRequestException {

    public ImportTooOldException(LocalDate earliestAllowedDate) {
        super("This import file contains a transaction dated before " + earliestAllowedDate + "; import refused");
    }
}
