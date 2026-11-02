package com.personalcashflow.imports.format;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class DefaultCsvBankFormatTest {

    private final DefaultCsvBankFormat format = new DefaultCsvBankFormat();

    @Test
    void headerLineCount_shouldSkipFiveMetadataLinesTheBlankLineAndTheColumnHeader() {
        assertThat(format.headerLineCount()).isEqualTo(7);
    }

    @Test
    void charset_shouldBeWindows1252() {
        assertThat(format.charset()).isEqualTo(Charset.forName("windows-1252"));
    }

    @Test
    void parseLine_withValidRow_shouldReturnImportedRow() {
        var result = format.parseLine("05/09/2026;\"CARREFOUR\";-81,24");

        assertThat(result).isPresent();
        assertThat(result.get().date()).isEqualTo(LocalDate.of(2026, 9, 5));
        assertThat(result.get().label()).isEqualTo("CARREFOUR");
        assertThat(result.get().amount()).isEqualByComparingTo(BigDecimal.valueOf(-81.24));
    }

    @Test
    void parseLine_withPositiveAmount_shouldReturnImportedRow() {
        var result = format.parseLine("08/09/2026;\"SALAIRE\";2850,00");

        assertThat(result).isPresent();
        assertThat(result.get().amount()).isEqualByComparingTo(BigDecimal.valueOf(2850));
    }

    @Test
    void parseLine_withUnquotedLabel_shouldStillParse() {
        var result = format.parseLine("05/09/2026;CARREFOUR;-81,24");

        assertThat(result).isPresent();
        assertThat(result.get().label()).isEqualTo("CARREFOUR");
    }

    @Test
    void parseLine_withThousandsSeparatorInAmount_shouldParse() {
        var result = format.parseLine("05/09/2026;\"VIREMENT\";1 616,00");

        assertThat(result).isPresent();
        assertThat(result.get().amount()).isEqualByComparingTo(BigDecimal.valueOf(1616));
    }

    @Test
    void parseLine_withWrongColumnCount_shouldReturnEmpty() {
        assertThat(format.parseLine("05/09/2026;CARREFOUR")).isEmpty();
    }

    @Test
    void parseLine_withInvalidDate_shouldReturnEmpty() {
        assertThat(format.parseLine("2026-09-05;CARREFOUR;-81,24")).isEmpty();
    }

    @Test
    void parseLine_withZeroAmount_shouldReturnEmpty() {
        assertThat(format.parseLine("05/09/2026;CARREFOUR;0")).isEmpty();
    }

    @Test
    void parseLine_withBlankLine_shouldReturnEmpty() {
        assertThat(format.parseLine("   ")).isEmpty();
    }

    @Test
    void parseLine_withValidRow_shouldDeriveAStableImportHashFromDateLabelAmount() {
        var result = format.parseLine("05/09/2026;\"CARREFOUR\";-81,24");

        assertThat(result).isPresent();
        assertThat(result.get().fitid()).isEqualTo("2026-09-05|carrefour|-81.24");
    }
}
