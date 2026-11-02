package com.personalcashflow.imports.format;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ImportedRow(LocalDate date, String label, BigDecimal amount, String fitid, String rawRecord) {
}
