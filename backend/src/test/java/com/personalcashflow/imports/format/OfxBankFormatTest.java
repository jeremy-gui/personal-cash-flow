package com.personalcashflow.imports.format;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.personalcashflow.imports.InvalidOfxFileException;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OfxBankFormatTest {

    private final OfxBankFormat format = new OfxBankFormat();

    private static String ofxDocument(String... stmttrns) {
        StringBuilder sb = new StringBuilder();
        sb.append("OFXHEADER:100\r\n")
                .append("DATA:OFXSGML\r\n")
                .append("VERSION:102\r\n")
                .append("SECURITY:NONE\r\n")
                .append("ENCODING:USASCII\r\n")
                .append("CHARSET:1252\r\n")
                .append("COMPRESSION:NONE\r\n")
                .append("OLDFILEUID:NONE\r\n")
                .append("NEWFILEUID:NONE\r\n")
                .append("\r\n")
                .append("<OFX><SIGNONMSGSRSV1><SONRS><STATUS><CODE>0<SEVERITY>INFO</STATUS>")
                .append("<DTSERVER>20260916111634<LANGUAGE>FRE</SONRS></SIGNONMSGSRSV1>")
                .append("<BANKMSGSRSV1><STMTTRNRS><TRNUID>1<STATUS><CODE>0<SEVERITY>INFO</STATUS>")
                .append("<CLTCOOKIE>1<STMTRS><CURDEF>EUR<BANKACCTFROM><BANKID>00000<BRANCHID>00000")
                .append("<ACCTID>123<ACCTTYPE>CHECKING<ACCTKEY>11</BANKACCTFROM>")
                .append("<BANKTRANLIST><DTSTART>20260901<DTEND>20260916");
        for (String stmttrn : stmttrns) {
            sb.append(stmttrn);
        }
        sb.append("</BANKTRANLIST><LEDGERBAL><BALAMT>100.00<DTASOF>20260916</LEDGERBAL>")
                .append("</STMTRS></STMTTRNRS></BANKMSGSRSV1></OFX>");
        return sb.toString();
    }

    private static String stmttrn(String type, String datePosted, String amount, String fitid, String name) {
        return "<STMTTRN><TRNTYPE>%s<DTPOSTED>%s<TRNAMT>%s<FITID>%s<NAME>%s</STMTTRN>"
                .formatted(type, datePosted, amount, fitid, name);
    }

    private static ByteArrayInputStream stream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.US_ASCII));
    }

    @Test
    void parse_withValidRecords_shouldReturnImportedRows() {
        String ofx = ofxDocument(
                stmttrn("POS", "20260905", "-81.24", "FIT001", "CARREFOUR"),
                stmttrn("DIRECTDEP", "20260908", "2850.00", "FIT002", "SALAIRE"));

        OfxBankFormat.ParsedStatement statement = format.parse(stream(ofx));
        List<Optional<ImportedRow>> rows = statement.rows();

        assertThat(statement.accountNumber()).isEqualTo("123");
        assertThat(rows).hasSize(2);
        ImportedRow first = rows.get(0).orElseThrow();
        assertThat(first.date()).isEqualTo(LocalDate.of(2026, 9, 5));
        assertThat(first.label()).isEqualTo("CARREFOUR");
        assertThat(first.amount()).isEqualByComparingTo(BigDecimal.valueOf(-81.24));
        assertThat(first.fitid()).isEqualTo("FIT001");
        assertThat(first.rawRecord()).contains("FIT001");

        ImportedRow second = rows.get(1).orElseThrow();
        assertThat(second.amount()).isEqualByComparingTo(BigDecimal.valueOf(2850));
    }

    @Test
    void parse_withZeroAmountRecord_shouldReturnEmptyForThatRecord() {
        String ofx = ofxDocument(
                stmttrn("POS", "20260905", "-81.24", "FIT001", "CARREFOUR"),
                stmttrn("DEBIT", "20260906", "0.00", "FIT002", "NO-OP"));

        List<Optional<ImportedRow>> rows = format.parse(stream(ofx)).rows();

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).isPresent();
        assertThat(rows.get(1)).isEmpty();
    }

    @Test
    void parse_withInvalidContent_shouldThrowInvalidOfxFileException() {
        assertThatThrownBy(() -> format.parse(stream("this is not an ofx file")))
                .isInstanceOf(InvalidOfxFileException.class);
    }
}
