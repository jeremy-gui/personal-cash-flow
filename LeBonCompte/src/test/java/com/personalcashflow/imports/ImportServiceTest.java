package com.personalcashflow.imports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.personalcashflow.account.Account;
import com.personalcashflow.account.AccountNotFoundException;
import com.personalcashflow.account.AccountRepository;
import com.personalcashflow.account.AccountType;
import com.personalcashflow.imports.dto.ImportSummaryResponse;
import com.personalcashflow.imports.format.OfxBankFormat;
import com.personalcashflow.transaction.Transaction;
import com.personalcashflow.transaction.TransactionRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class ImportServiceTest {

    @Mock
    private ImportBatchRepository importBatchRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AccountRepository accountRepository;

    private ImportService service() {
        return new ImportService(importBatchRepository, transactionRepository, accountRepository, new OfxBankFormat());
    }

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

    @Test
    void importOfx_withNewRows_shouldCreateTransactionsAndSkipDuplicates() {
        Account account = new Account("Compte courant", AccountType.COURANT, BigDecimal.ZERO);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(transactionRepository.existsByAccountIdAndImportHash(any(), any())).thenReturn(false);
        // second transaction is a duplicate
        when(transactionRepository.existsByAccountIdAndImportHash(1L, "FIT002")).thenReturn(true);
        when(importBatchRepository.save(any(ImportBatch.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String ofx = ofxDocument(
                stmttrn("POS", "20260905", "-81.24", "FIT001", "CARREFOUR"),
                stmttrn("PAYMENT", "20260907", "-92.30", "FIT002", "EDF"),
                stmttrn("DIRECTDEP", "20260908", "2850.00", "FIT003", "SALAIRE"));
        MockMultipartFile file = new MockMultipartFile(
                "file", "releve.ofx", "application/x-ofx", ofx.getBytes(StandardCharsets.US_ASCII));

        ImportSummaryResponse summary = service().importOfx(1L, file);

        assertThat(summary.rowCount()).isEqualTo(3);
        assertThat(summary.createdCount()).isEqualTo(2);
        assertThat(summary.skippedCount()).isEqualTo(1);
        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, times(2)).save(captor.capture());
        Transaction first = captor.getAllValues().get(0);
        assertThat(first.getOriginalDate()).isEqualTo(LocalDate.of(2026, 9, 5));
        assertThat(first.getOriginalLabel()).isEqualTo("CARREFOUR");
        assertThat(first.getImportHash()).isEqualTo("FIT001");
    }

    @Test
    void importOfx_whenAccountMissing_shouldThrowAccountNotFoundException() {
        when(accountRepository.findById(99L)).thenReturn(Optional.empty());
        String ofx = ofxDocument(stmttrn("POS", "20260905", "-81.24", "FIT001", "CARREFOUR"));
        MockMultipartFile file = new MockMultipartFile(
                "file", "releve.ofx", "application/x-ofx", ofx.getBytes(StandardCharsets.US_ASCII));

        assertThatThrownBy(() -> service().importOfx(99L, file))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void importOfx_withEmptyFile_shouldThrowEmptyImportFileException() {
        MockMultipartFile file = new MockMultipartFile("file", "releve.ofx", "application/x-ofx", new byte[0]);

        assertThatThrownBy(() -> service().importOfx(1L, file))
                .isInstanceOf(EmptyImportFileException.class);
        verify(transactionRepository, never()).save(any());
    }
}
