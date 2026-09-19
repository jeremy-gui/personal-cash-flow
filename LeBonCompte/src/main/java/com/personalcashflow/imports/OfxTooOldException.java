package com.personalcashflow.imports;

import com.personalcashflow.common.BadRequestException;
import java.time.LocalDate;

public class OfxTooOldException extends BadRequestException {

    public OfxTooOldException(LocalDate earliestAllowedDate) {
        super("This OFX file contains a transaction dated before " + earliestAllowedDate + "; import refused");
    }
}
