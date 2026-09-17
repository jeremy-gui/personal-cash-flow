package com.personalcashflow.imports;

import com.personalcashflow.account.Account;
import com.personalcashflow.account.AccountRepository;
import com.personalcashflow.imports.dto.ImportSummaryResponse;
import com.personalcashflow.imports.format.ImportedRow;
import com.personalcashflow.imports.format.OfxBankFormat;
import com.personalcashflow.transaction.Transaction;
import com.personalcashflow.transaction.TransactionRepository;
import com.personalcashflow.transaction.TransactionType;
import java.io.IOException;
import java.io.UncheckedIOException;
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
    private final OfxBankFormat ofxBankFormat;

    public ImportService(
            ImportBatchRepository importBatchRepository,
            TransactionRepository transactionRepository,
            AccountRepository accountRepository,
            OfxBankFormat ofxBankFormat) {
        this.importBatchRepository = importBatchRepository;
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.ofxBankFormat = ofxBankFormat;
    }

    public ImportSummaryResponse importOfx(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new EmptyImportFileException();
        }

        OfxBankFormat.ParsedStatement statement = parse(file);
        Account account = accountRepository.findByAccountNumber(statement.accountNumber())
                .orElseThrow(() -> new UnmatchedOfxAccountException(statement.accountNumber()));
        Long accountId = account.getId();

        List<Optional<ImportedRow>> parsedRows = statement.rows();
        int rowCount = parsedRows.size();
        int createdCount = 0;
        int skippedCount = 0;

        for (Optional<ImportedRow> parsed : parsedRows) {
            if (parsed.isEmpty()) {
                skippedCount++;
                continue;
            }

            ImportedRow row = parsed.get();
            if (transactionRepository.existsByAccountIdAndImportHash(accountId, row.fitid())) {
                skippedCount++;
                continue;
            }

            TransactionType type = row.amount().signum() < 0 ? TransactionType.DEPENSE : TransactionType.REVENU;
            Transaction transaction = Transaction.imported(
                    row.date(), row.label(), row.amount(), account, type, row.fitid(), row.rawRecord());
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

    private OfxBankFormat.ParsedStatement parse(MultipartFile file) {
        try (var inputStream = file.getInputStream()) {
            return ofxBankFormat.parse(inputStream);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read uploaded file", e);
        }
    }
}
