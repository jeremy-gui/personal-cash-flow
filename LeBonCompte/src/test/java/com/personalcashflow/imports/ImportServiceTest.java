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
import com.personalcashflow.imports.format.DefaultCsvBankFormat;
import com.personalcashflow.transaction.Transaction;
import com.personalcashflow.transaction.TransactionRepository;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
        return new ImportService(importBatchRepository, transactionRepository, accountRepository, new DefaultCsvBankFormat());
    }

    @Test
    void importCsv_withNewRows_shouldCreateTransactionsAndSkipDuplicatesAndMalformed() {
        Account account = new Account("Compte courant", AccountType.COURANT, BigDecimal.ZERO);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(transactionRepository.existsByAccountIdAndImportHash(any(), any())).thenReturn(false);
        // second data row is a duplicate
        when(transactionRepository.existsByAccountIdAndImportHash(1L, "2026-09-07|edf|-92.3")).thenReturn(true);
        when(importBatchRepository.save(any(ImportBatch.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String csv = String.join("\r\n",
                "Numero Compte   ;00000000A00",
                "Type         ;CCP",
                "Compte tenu en  ;euros",
                "Date            ;07/09/2026",
                "Solde (EUROS)   ;5045,58",
                "",
                "Date;Libelle;Montant(EUROS)",
                "05/09/2026;\"CARREFOUR\";-81,24",
                "07/09/2026;\"EDF\";-92,30",
                "not-a-row",
                "08/09/2026;\"SALAIRE\";2850,00");
        MockMultipartFile file = new MockMultipartFile(
                "file", "releve.csv", "text/csv", csv.getBytes(Charset.forName("windows-1252")));

        ImportSummaryResponse summary = service().importCsv(1L, file);

        assertThat(summary.rowCount()).isEqualTo(4);
        assertThat(summary.createdCount()).isEqualTo(2);
        assertThat(summary.skippedCount()).isEqualTo(2);
        verify(transactionRepository, times(2)).save(any(Transaction.class));
    }

    @Test
    void importCsv_whenAccountMissing_shouldThrowAccountNotFoundException() {
        when(accountRepository.findById(99L)).thenReturn(Optional.empty());
        MockMultipartFile file = new MockMultipartFile(
                "file", "releve.csv", "text/csv", "date;libelle;montant\n05/09/2026;X;-1".getBytes(Charset.forName("windows-1252")));

        assertThatThrownBy(() -> service().importCsv(99L, file))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void importCsv_withEmptyFile_shouldThrowEmptyImportFileException() {
        MockMultipartFile file = new MockMultipartFile("file", "releve.csv", "text/csv", new byte[0]);

        assertThatThrownBy(() -> service().importCsv(1L, file))
                .isInstanceOf(EmptyImportFileException.class);
        verify(transactionRepository, never()).save(any());
    }
}
