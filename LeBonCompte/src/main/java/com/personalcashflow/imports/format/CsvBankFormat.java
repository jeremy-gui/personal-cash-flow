package com.personalcashflow.imports.format;

import java.util.Optional;

/**
 * Parses one data line of a bank's CSV export. Kept as an interface so a different bank's
 * format can be added later without touching {@code ImportService}.
 */
public interface CsvBankFormat {

    /** Number of leading lines to skip unconditionally (e.g. the column header row). */
    int headerLineCount();

    /** Empty if the line is blank or does not match the expected format. */
    Optional<ImportedRow> parseLine(String line);
}
