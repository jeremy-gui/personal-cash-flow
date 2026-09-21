package com.personalcashflow.imports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.personalcashflow.account.Account;
import com.personalcashflow.account.AccountRepository;
import com.personalcashflow.account.AccountType;
import com.personalcashflow.account.AccountNotFoundException;
import com.personalcashflow.imports.dto.ImportSummaryResponse;
import com.personalcashflow.imports.dto.ReconciliationSummaryResponse;
import com.personalcashflow.imports.format.DefaultCsvBankFormat;
import com.personalcashflow.imports.format.OfxBankFormat;
import com.personalcashflow.transaction.CategorySuggestionService;
import com.personalcashflow.transaction.Transaction;
import com.personalcashflow.transaction.TransactionRepository;
import com.personalcashflow.transaction.TransactionStatus;
import com.personalcashflow.transaction.TransactionType;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
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

    @Mock
    private CategorySuggestionService categorySuggestionService;

    private ImportService service() {
        return new ImportService(
                importBatchRepository, transactionRepository, accountRepository,
                new OfxBankFormat(), new DefaultCsvBankFormat(), categorySuggestionService);
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

    private static void setId(Account account, Long id) {
        try {
            Field idField = Account.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(account, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void importOfx_withNewRows_shouldCreateTransactionsAndSkipDuplicates() {
        Account account = new Account("Compte courant", AccountType.COURANT, BigDecimal.ZERO, "123");
        setId(account, 1L);
        when(accountRepository.findByAccountNumber("123")).thenReturn(Optional.of(account));
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

        ImportSummaryResponse summary = service().importOfx(file);

        assertThat(summary.rowCount()).isEqualTo(3);
        assertThat(summary.createdCount()).isEqualTo(2);
        assertThat(summary.skippedCount()).isEqualTo(1);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Transaction>> captor = ArgumentCaptor.forClass(List.class);
        verify(transactionRepository).saveAll(captor.capture());
        Transaction first = captor.getValue().get(0);
        assertThat(first.getOriginalDate()).isEqualTo(LocalDate.of(2026, 9, 5));
        assertThat(first.getOriginalLabel()).isEqualTo("CARREFOUR");
        assertThat(first.getImportHash()).isEqualTo("FIT001");
    }

    @Test
    void importOfx_withTwoRowsSharingAmountDateAndLabel_shouldCreateBothWhenFitidsDiffer() {
        // a real bank export: two genuinely separate transactions can share amount/date/label,
        // but each still gets its own unique FITID from the bank.
        Account account = new Account("Compte courant", AccountType.COURANT, BigDecimal.ZERO, "123");
        setId(account, 1L);
        when(accountRepository.findByAccountNumber("123")).thenReturn(Optional.of(account));
        when(transactionRepository.existsByAccountIdAndImportHash(any(), any())).thenReturn(false);
        when(importBatchRepository.save(any(ImportBatch.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String ofx = ofxDocument(
                stmttrn("POS", "20260905", "-20.00", "FIT001", "CARREFOUR"),
                stmttrn("POS", "20260905", "-20.00", "FIT002", "CARREFOUR"));
        MockMultipartFile file = new MockMultipartFile(
                "file", "releve.ofx", "application/x-ofx", ofx.getBytes(StandardCharsets.US_ASCII));

        ImportSummaryResponse summary = service().importOfx(file);

        assertThat(summary.createdCount()).isEqualTo(2);
        assertThat(summary.skippedCount()).isZero();
    }

    @Test
    void importOfx_withSameAmountDateAndLabelPrefixAsAnExistingRow_shouldSkipAsDuplicate() {
        // no FITID overlap at all - the hash check alone would not catch this
        Account account = new Account("Compte courant", AccountType.COURANT, BigDecimal.ZERO, "123");
        setId(account, 1L);
        when(accountRepository.findByAccountNumber("123")).thenReturn(Optional.of(account));
        when(transactionRepository.existsByAccountIdAndImportHash(any(), any())).thenReturn(false);
        when(transactionRepository.existsSimilarByAccountAmountOriginalDateAndLabelPrefix(
                        1L, new BigDecimal("-81.24"), LocalDate.of(2026, 9, 5), "CARREFOUR"))
                .thenReturn(true);
        when(importBatchRepository.save(any(ImportBatch.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String ofx = ofxDocument(stmttrn("POS", "20260905", "-81.24", "NEWFIT99", "CARREFOUR"));
        MockMultipartFile file = new MockMultipartFile(
                "file", "releve.ofx", "application/x-ofx", ofx.getBytes(StandardCharsets.US_ASCII));

        ImportSummaryResponse summary = service().importOfx(file);

        assertThat(summary.createdCount()).isZero();
        assertThat(summary.skippedCount()).isEqualTo(1);
        verify(transactionRepository).saveAll(List.of());
    }

    @Test
    void importOfx_withRowBeforeSeptember2026_shouldThrowImportTooOldExceptionAndSaveNothing() {
        String ofx = ofxDocument(
                stmttrn("POS", "20260905", "-81.24", "FIT001", "CARREFOUR"),
                stmttrn("POS", "20260831", "-12.00", "FIT002", "BOULANGERIE"));
        MockMultipartFile file = new MockMultipartFile(
                "file", "releve.ofx", "application/x-ofx", ofx.getBytes(StandardCharsets.US_ASCII));

        assertThatThrownBy(() -> service().importOfx(file))
                .isInstanceOf(ImportTooOldException.class);
        verify(transactionRepository, never()).saveAll(any());
        verify(accountRepository, never()).findByAccountNumber(any());
    }

    @Test
    void importOfx_withRowExactlyOnSeptember2026_shouldNotThrow() {
        Account account = new Account("Compte courant", AccountType.COURANT, BigDecimal.ZERO, "123");
        setId(account, 1L);
        when(accountRepository.findByAccountNumber("123")).thenReturn(Optional.of(account));
        when(transactionRepository.existsByAccountIdAndImportHash(any(), any())).thenReturn(false);
        when(importBatchRepository.save(any(ImportBatch.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String ofx = ofxDocument(stmttrn("POS", "20260901", "-81.24", "FIT001", "CARREFOUR"));
        MockMultipartFile file = new MockMultipartFile(
                "file", "releve.ofx", "application/x-ofx", ofx.getBytes(StandardCharsets.US_ASCII));

        ImportSummaryResponse summary = service().importOfx(file);

        assertThat(summary.createdCount()).isEqualTo(1);
    }

    @Test
    void importOfx_whenNoAccountMatchesAcctId_shouldThrowUnmatchedOfxAccountException() {
        when(accountRepository.findByAccountNumber("123")).thenReturn(Optional.empty());
        String ofx = ofxDocument(stmttrn("POS", "20260905", "-81.24", "FIT001", "CARREFOUR"));
        MockMultipartFile file = new MockMultipartFile(
                "file", "releve.ofx", "application/x-ofx", ofx.getBytes(StandardCharsets.US_ASCII));

        assertThatThrownBy(() -> service().importOfx(file))
                .isInstanceOf(UnmatchedOfxAccountException.class);
        verify(transactionRepository, never()).saveAll(any());
    }

    @Test
    void importOfx_withEmptyFile_shouldThrowEmptyImportFileException() {
        MockMultipartFile file = new MockMultipartFile("file", "releve.ofx", "application/x-ofx", new byte[0]);

        assertThatThrownBy(() -> service().importOfx(file))
                .isInstanceOf(EmptyImportFileException.class);
        verify(transactionRepository, never()).saveAll(any());
    }

    private static String csvDocument(String... dataLines) {
        StringBuilder sb = new StringBuilder();
        sb.append("Compte;123456\r\n")
                .append("Du;01/09/2026\r\n")
                .append("Au;30/09/2026\r\n")
                .append("Solde;1000,00\r\n")
                .append("Devise;EUR\r\n")
                .append("\r\n")
                .append("Date;Libelle;Montant(EUROS)\r\n");
        for (String line : dataLines) {
            sb.append(line).append("\r\n");
        }
        return sb.toString();
    }

    private static MockMultipartFile csvFile(String content) {
        return new MockMultipartFile(
                "file", "releve.csv", "text/csv", content.getBytes(Charset.forName("windows-1252")));
    }

    @Test
    void importCsv_withNewRows_shouldCreateTransactionsAndSkipDuplicates() {
        Account account = new Account("Compte courant", AccountType.COURANT, BigDecimal.ZERO, "123");
        setId(account, 1L);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(transactionRepository.existsByAccountIdAndImportHash(any(), any())).thenReturn(false);
        when(importBatchRepository.save(any(ImportBatch.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MockMultipartFile file = csvFile(csvDocument(
                "05/09/2026;\"CARREFOUR\";-81,24",
                "07/09/2026;\"SALAIRE\";2850,00"));

        ImportSummaryResponse summary = service().importCsv(1L, file);

        assertThat(summary.rowCount()).isEqualTo(2);
        assertThat(summary.createdCount()).isEqualTo(2);
        assertThat(summary.skippedCount()).isZero();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Transaction>> captor = ArgumentCaptor.forClass(List.class);
        verify(transactionRepository).saveAll(captor.capture());
        Transaction first = captor.getValue().get(0);
        assertThat(first.getOriginalDate()).isEqualTo(LocalDate.of(2026, 9, 5));
        assertThat(first.getOriginalLabel()).isEqualTo("CARREFOUR");
        assertThat(first.getImportHash()).isEqualTo("2026-09-05|carrefour|-81.24");
    }

    @Test
    void importCsv_shouldOfferNewlyCreatedRowsToCategorySuggestionBeforeSaving() {
        Account account = new Account("Compte courant", AccountType.COURANT, BigDecimal.ZERO, "123");
        setId(account, 1L);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(transactionRepository.existsByAccountIdAndImportHash(any(), any())).thenReturn(false);
        when(importBatchRepository.save(any(ImportBatch.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MockMultipartFile file = csvFile(csvDocument("05/09/2026;\"CARREFOUR\";-81,24"));

        service().importCsv(1L, file);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Transaction>> captor = ArgumentCaptor.forClass(List.class);
        verify(categorySuggestionService).suggestCategories(eq(1L), captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        verify(transactionRepository).saveAll(captor.getValue());
    }

    @Test
    void importCsv_withTwoIdenticalRowsInTheSameFile_shouldCreateBothWithDistinctHashes() {
        Account account = new Account("Compte courant", AccountType.COURANT, BigDecimal.ZERO, "123");
        setId(account, 1L);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(transactionRepository.existsByAccountIdAndImportHash(any(), any())).thenReturn(false);
        when(importBatchRepository.save(any(ImportBatch.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MockMultipartFile file = csvFile(csvDocument(
                "05/09/2026;\"CARREFOUR\";-81,24",
                "05/09/2026;\"CARREFOUR\";-81,24",
                "07/09/2026;\"SALAIRE\";2850,00"));

        ImportSummaryResponse summary = service().importCsv(1L, file);

        assertThat(summary.rowCount()).isEqualTo(3);
        assertThat(summary.createdCount()).isEqualTo(3);
        assertThat(summary.skippedCount()).isZero();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Transaction>> captor = ArgumentCaptor.forClass(List.class);
        verify(transactionRepository).saveAll(captor.capture());
        List<Transaction> saved = captor.getValue();
        assertThat(saved).extracting(Transaction::getImportHash).containsExactlyInAnyOrder(
                "2026-09-05|carrefour|-81.24", "2026-09-05|carrefour|-81.24|2", "2026-09-07|salaire|2850");
    }

    @Test
    void importCsv_reimportingFileWithIdenticalRows_shouldSkipBothAsAlreadyImported() {
        Account account = new Account("Compte courant", AccountType.COURANT, BigDecimal.ZERO, "123");
        setId(account, 1L);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(transactionRepository.existsByAccountIdAndImportHash(1L, "2026-09-05|carrefour|-81.24")).thenReturn(true);
        when(transactionRepository.existsByAccountIdAndImportHash(1L, "2026-09-05|carrefour|-81.24|2")).thenReturn(true);
        when(importBatchRepository.save(any(ImportBatch.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MockMultipartFile file = csvFile(csvDocument(
                "05/09/2026;\"CARREFOUR\";-81,24",
                "05/09/2026;\"CARREFOUR\";-81,24"));

        ImportSummaryResponse summary = service().importCsv(1L, file);

        assertThat(summary.createdCount()).isZero();
        assertThat(summary.skippedCount()).isEqualTo(2);
        verify(transactionRepository).saveAll(List.of());
    }

    @Test
    void importCsv_withSameAmountDateAndLabelPrefixAsAnExistingRow_shouldSkipAsDuplicate() {
        // e.g. the same real purchase already imported via OFX, whose FITID-based hash is unrelated
        Account account = new Account("Compte courant", AccountType.COURANT, BigDecimal.ZERO, "123");
        setId(account, 1L);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(transactionRepository.existsByAccountIdAndImportHash(any(), any())).thenReturn(false);
        when(transactionRepository.existsSimilarByAccountAmountOriginalDateAndLabelPrefix(
                        1L, new BigDecimal("-81.24"), LocalDate.of(2026, 9, 5), "CARREFOUR"))
                .thenReturn(true);
        when(importBatchRepository.save(any(ImportBatch.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MockMultipartFile file = csvFile(csvDocument("05/09/2026;\"CARREFOUR\";-81,24"));

        ImportSummaryResponse summary = service().importCsv(1L, file);

        assertThat(summary.createdCount()).isZero();
        assertThat(summary.skippedCount()).isEqualTo(1);
        verify(transactionRepository).saveAll(List.of());
    }

    @Test
    void importCsv_withRowBeforeSeptember2026_shouldThrowImportTooOldExceptionAndSaveNothing() {
        MockMultipartFile file = csvFile(csvDocument("31/08/2026;\"BOULANGERIE\";-12,00"));

        assertThatThrownBy(() -> service().importCsv(1L, file))
                .isInstanceOf(ImportTooOldException.class);
        verify(transactionRepository, never()).saveAll(any());
        verify(accountRepository, never()).findById(any());
    }

    @Test
    void importCsv_whenAccountMissing_shouldThrowAccountNotFoundException() {
        when(accountRepository.findById(99L)).thenReturn(Optional.empty());
        MockMultipartFile file = csvFile(csvDocument("05/09/2026;\"CARREFOUR\";-81,24"));

        assertThatThrownBy(() -> service().importCsv(99L, file))
                .isInstanceOf(AccountNotFoundException.class);
        verify(transactionRepository, never()).saveAll(any());
    }

    @Test
    void importCsv_withEmptyFile_shouldThrowEmptyImportFileException() {
        MockMultipartFile file = new MockMultipartFile("file", "releve.csv", "text/csv", new byte[0]);

        assertThatThrownBy(() -> service().importCsv(1L, file))
                .isInstanceOf(EmptyImportFileException.class);
        verify(transactionRepository, never()).saveAll(any());
    }

    @Test
    void importCsv_withMalformedLine_shouldSkipIt() {
        Account account = new Account("Compte courant", AccountType.COURANT, BigDecimal.ZERO, "123");
        setId(account, 1L);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(transactionRepository.existsByAccountIdAndImportHash(any(), any())).thenReturn(false);
        when(importBatchRepository.save(any(ImportBatch.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MockMultipartFile file = csvFile(csvDocument("not;a;valid;csv;line", "05/09/2026;\"CARREFOUR\";-81,24"));

        ImportSummaryResponse summary = service().importCsv(1L, file);

        assertThat(summary.rowCount()).isEqualTo(2);
        assertThat(summary.createdCount()).isEqualTo(1);
        assertThat(summary.skippedCount()).isEqualTo(1);
    }

    private static Transaction candidate(LocalDate date, String label, String amount) {
        Account account = new Account("Compte courant", AccountType.COURANT, BigDecimal.ZERO, "123");
        return Transaction.imported(date, label, new BigDecimal(amount), account, TransactionType.DEPENSE, "XLSX-x", "raw");
    }

    private void stubAccountAndCandidates(List<Transaction> candidates) {
        Account account = new Account("Compte courant", AccountType.COURANT, BigDecimal.ZERO, "123");
        setId(account, 1L);
        when(accountRepository.findByAccountNumber("123")).thenReturn(Optional.of(account));
        when(transactionRepository.findByAccountIdAndDateBetweenAndStatusAndImportHashStartingWith(
                        any(), any(), any(), any(), any()))
                .thenReturn(candidates);
    }

    @Test
    void reconcileOriginalLabels_withSingleMatch_shouldEnrichWithoutTouchingDateLabelAmount() {
        Transaction existing = candidate(LocalDate.of(2026, 7, 1), "leclerc", "-64.87");
        stubAccountAndCandidates(List.of(existing));
        when(transactionRepository.existsByAccountIdAndImportHash(any(), any())).thenReturn(false);

        String ofx = ofxDocument(stmttrn("POS", "20260705", "-64.87", "REALFIT1", "ACHAT CB LECLERC 05/07"));
        MockMultipartFile file = new MockMultipartFile(
                "file", "releve.ofx", "application/x-ofx", ofx.getBytes(StandardCharsets.US_ASCII));

        ReconciliationSummaryResponse summary = service().reconcileOriginalLabels(file);

        assertThat(summary.matched()).isEqualTo(1);
        assertThat(summary.unmatched()).isZero();
        assertThat(summary.ambiguousUnresolved()).isZero();
        assertThat(existing.getDate()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(existing.getLabel()).isEqualTo("leclerc");
        assertThat(existing.getAmount()).isEqualByComparingTo(new BigDecimal("-64.87"));
        assertThat(existing.getOriginalLabel()).isEqualTo("ACHAT CB LECLERC 05/07");
        assertThat(existing.getOriginalDate()).isEqualTo(LocalDate.of(2026, 7, 5));
        assertThat(existing.getImportHash()).isEqualTo("REALFIT1");
    }

    @Test
    void reconcileOriginalLabels_withAmbiguousAmount_shouldPickTheBestLabelMatch() {
        Transaction leclerc = candidate(LocalDate.of(2026, 7, 1), "leclerc", "-20.00");
        Transaction parking = candidate(LocalDate.of(2026, 7, 1), "parking", "-20.00");
        stubAccountAndCandidates(List.of(leclerc, parking));
        when(transactionRepository.existsByAccountIdAndImportHash(any(), any())).thenReturn(false);

        String ofx = ofxDocument(stmttrn("POS", "20260705", "-20.00", "REALFIT1", "ACHAT CB LECLERC 05/07"));
        MockMultipartFile file = new MockMultipartFile(
                "file", "releve.ofx", "application/x-ofx", ofx.getBytes(StandardCharsets.US_ASCII));

        ReconciliationSummaryResponse summary = service().reconcileOriginalLabels(file);

        assertThat(summary.matched()).isEqualTo(1);
        assertThat(leclerc.getOriginalLabel()).isEqualTo("ACHAT CB LECLERC 05/07");
        assertThat(parking.getOriginalLabel()).isEqualTo("parking");
        assertThat(parking.getImportHash()).isEqualTo("XLSX-x");
    }

    @Test
    void reconcileOriginalLabels_withNoLabelSimilarityWinner_shouldLeaveBothUnresolved() {
        Transaction first = candidate(LocalDate.of(2026, 7, 1), "tam", "-20.00");
        Transaction second = candidate(LocalDate.of(2026, 7, 1), "verre Leo", "-20.00");
        stubAccountAndCandidates(List.of(first, second));
        when(transactionRepository.existsByAccountIdAndImportHash(any(), any())).thenReturn(false);

        String ofx = ofxDocument(stmttrn("POS", "20260705", "-20.00", "REALFIT1", "ACHAT CB INCONNU"));
        MockMultipartFile file = new MockMultipartFile(
                "file", "releve.ofx", "application/x-ofx", ofx.getBytes(StandardCharsets.US_ASCII));

        ReconciliationSummaryResponse summary = service().reconcileOriginalLabels(file);

        assertThat(summary.ambiguousUnresolved()).isEqualTo(1);
        assertThat(summary.matched()).isZero();
        assertThat(first.getOriginalLabel()).isEqualTo("tam");
        assertThat(second.getOriginalLabel()).isEqualTo("verre Leo");
    }

    @Test
    void reconcileOriginalLabels_withNoCandidateInOwnOrAdjacentMonth_shouldReportUnmatched() {
        stubAccountAndCandidates(List.of());
        when(transactionRepository.existsByAccountIdAndImportHash(any(), any())).thenReturn(false);

        String ofx = ofxDocument(stmttrn("POS", "20260705", "-64.87", "REALFIT1", "ACHAT CB LECLERC 05/07"));
        MockMultipartFile file = new MockMultipartFile(
                "file", "releve.ofx", "application/x-ofx", ofx.getBytes(StandardCharsets.US_ASCII));

        ReconciliationSummaryResponse summary = service().reconcileOriginalLabels(file);

        assertThat(summary.unmatched()).isEqualTo(1);
        assertThat(summary.unmatchedDetails()).hasSize(1);
        assertThat(summary.unmatchedDetails().get(0).amount()).isEqualByComparingTo(new BigDecimal("-64.87"));
    }

    @Test
    void reconcileOriginalLabels_withNoCandidateInOwnMonth_shouldFallBackToAdjacentMonth() {
        Transaction loggedNextMonth = candidate(LocalDate.of(2026, 8, 1), "leclerc", "-64.87");
        stubAccountAndCandidates(List.of(loggedNextMonth));
        when(transactionRepository.existsByAccountIdAndImportHash(any(), any())).thenReturn(false);

        // posted 2026-07-31 by the bank, but the user logged it in their August tab
        String ofx = ofxDocument(stmttrn("POS", "20260731", "-64.87", "REALFIT1", "ACHAT CB LECLERC 31/07"));
        MockMultipartFile file = new MockMultipartFile(
                "file", "releve.ofx", "application/x-ofx", ofx.getBytes(StandardCharsets.US_ASCII));

        ReconciliationSummaryResponse summary = service().reconcileOriginalLabels(file);

        assertThat(summary.matched()).isEqualTo(1);
        assertThat(loggedNextMonth.getDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(loggedNextMonth.getOriginalLabel()).isEqualTo("ACHAT CB LECLERC 31/07");
    }

    @Test
    void reconcileOriginalLabels_whenFitidAlreadyRecorded_shouldSkipAsAlreadyReconciled() {
        stubAccountAndCandidates(List.of());
        when(transactionRepository.existsByAccountIdAndImportHash(1L, "REALFIT1")).thenReturn(true);

        String ofx = ofxDocument(stmttrn("POS", "20260705", "-64.87", "REALFIT1", "ACHAT CB LECLERC 05/07"));
        MockMultipartFile file = new MockMultipartFile(
                "file", "releve.ofx", "application/x-ofx", ofx.getBytes(StandardCharsets.US_ASCII));

        ReconciliationSummaryResponse summary = service().reconcileOriginalLabels(file);

        assertThat(summary.alreadyReconciled()).isEqualTo(1);
        assertThat(summary.matched()).isZero();
        assertThat(summary.unmatched()).isZero();
    }
}
