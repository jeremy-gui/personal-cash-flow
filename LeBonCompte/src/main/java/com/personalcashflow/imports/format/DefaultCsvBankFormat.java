package com.personalcashflow.imports.format;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * The bank's own CSV export format: {@code date;libelle;montant}, semicolon-separated,
 * dates as dd/MM/yyyy, amounts already signed with a dot decimal separator.
 */
@Component
public class DefaultCsvBankFormat implements CsvBankFormat {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Override
    public int headerLineCount() {
        return 1;
    }

    @Override
    public Optional<ImportedRow> parseLine(String line) {
        if (line == null || line.isBlank()) {
            return Optional.empty();
        }
        String[] parts = line.split(";", -1);
        if (parts.length != 3) {
            return Optional.empty();
        }

        Optional<LocalDate> date = parseDate(parts[0].trim());
        String label = parts[1].trim();
        BigDecimal amount = parseAmount(parts[2].trim());

        if (date.isEmpty() || label.isEmpty() || amount == null || amount.signum() == 0) {
            return Optional.empty();
        }
        return Optional.of(new ImportedRow(date.get(), label, amount, line));
    }

    private static Optional<LocalDate> parseDate(String value) {
        try {
            return Optional.of(LocalDate.parse(value, DATE_FORMAT));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    private static BigDecimal parseAmount(String value) {
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
