package com.personalcashflow.imports;

import com.personalcashflow.account.Account;
import com.personalcashflow.account.AccountNotFoundException;
import com.personalcashflow.account.AccountRepository;
import com.personalcashflow.imports.dto.ImportSummaryResponse;
import com.personalcashflow.imports.format.CsvBankFormat;
import com.personalcashflow.imports.format.ImportedRow;
import com.personalcashflow.transaction.Transaction;
import com.personalcashflow.transaction.TransactionRepository;
import com.personalcashflow.transaction.TransactionType;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional
public class ImportService {

    private final ImportBatchRepository importBatchRepository;
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final CsvBankFormat csvBankFormat;

    public ImportService(
            ImportBatchRepository importBatchRepository,
            TransactionRepository transactionRepository,
            AccountRepository accountRepository,
            CsvBankFormat csvBankFormat) {
        this.importBatchRepository = importBatchRepository;
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.csvBankFormat = csvBankFormat;
    }

    public ImportSummaryResponse importCsv(Long accountId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new EmptyImportFileException();
        }
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        List<String> lines = readLines(file);
        List<String> dataLines = lines.size() > csvBankFormat.headerLineCount()
                ? lines.subList(csvBankFormat.headerLineCount(), lines.size())
                : List.of();
        int rowCount = 0;
        int createdCount = 0;
        int skippedCount = 0;

        for (String line : dataLines) {
            if (line.isBlank()) {
                continue;
            }
            rowCount++;

            Optional<ImportedRow> parsed = csvBankFormat.parseLine(line);
            if (parsed.isEmpty()) {
                skippedCount++;
                continue;
            }

            ImportedRow row = parsed.get();
            String importHash = buildImportHash(row);
            if (transactionRepository.existsByAccountIdAndImportHash(accountId, importHash)) {
                skippedCount++;
                continue;
            }

            TransactionType type = row.amount().signum() < 0 ? TransactionType.DEPENSE : TransactionType.REVENU;
            Transaction transaction = Transaction.imported(
                    row.date(), row.label(), row.amount(), account, type, importHash, row.rawLine());
            transactionRepository.save(transaction);
            createdCount++;
        }

        ImportBatch batch = new ImportBatch(account, file.getOriginalFilename(), rowCount, createdCount, skippedCount);
        importBatchRepository.save(batch);
        return ImportSummaryResponse.from(batch);
    }

    @Transactional(readOnly = true)
    public List<ImportSummaryResponse> history() {
        return importBatchRepository.findAllByOrderByImportedAtDesc().stream()
                .map(ImportSummaryResponse::from)
                .toList();
    }

    private static String buildImportHash(ImportedRow row) {
        return row.date() + "|" + row.label().trim().toLowerCase() + "|" + row.amount().stripTrailingZeros().toPlainString();
    }

    private static List<String> readLines(MultipartFile file) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines().toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read uploaded file", e);
        }
    }
}
