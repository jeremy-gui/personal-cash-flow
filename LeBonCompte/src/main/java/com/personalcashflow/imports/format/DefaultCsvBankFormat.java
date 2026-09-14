package com.personalcashflow.imports.format;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * La Banque Postale's CSV export format: 5 metadata lines, a blank line, then a
 * {@code Date;Libelle;Montant(EUROS)} header, semicolon-separated, dates as dd/MM/yyyy,
 * quoted labels, amounts already signed with a French comma decimal separator. The file
 * itself is Windows-1252 encoded, not UTF-8.
 */
@Component
public class DefaultCsvBankFormat implements CsvBankFormat {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Charset CHARSET = Charset.forName("windows-1252");

    // Thousands-separator whitespace variants French bank exports may use: regular space,
    // non-breaking space (U+00A0), narrow no-break space (U+202F).
    private static final String THOUSANDS_SEPARATORS = "[\\s\u00A0\u202F]";

    @Override
    public int headerLineCount() {
        return 7;
    }

    @Override
    public Charset charset() {
        return CHARSET;
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
        String label = stripQuotes(parts[1].trim());
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

    private static String stripQuotes(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1).trim();
        }
        return value;
    }

    private static BigDecimal parseAmount(String value) {
        String normalized = value.replaceAll(THOUSANDS_SEPARATORS, "").replace(',', '.');
        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
