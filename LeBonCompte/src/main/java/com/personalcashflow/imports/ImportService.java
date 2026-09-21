package com.personalcashflow.imports;

import com.personalcashflow.account.Account;
import com.personalcashflow.account.AccountNotFoundException;
import com.personalcashflow.account.AccountRepository;
import com.personalcashflow.imports.dto.ImportSummaryResponse;
import com.personalcashflow.imports.dto.ReconciliationSummaryResponse;
import com.personalcashflow.imports.dto.ReconciliationSummaryResponse.UnresolvedRow;
import com.personalcashflow.imports.format.CsvBankFormat;
import com.personalcashflow.imports.format.ImportedRow;
import com.personalcashflow.imports.format.OfxBankFormat;
import com.personalcashflow.transaction.Transaction;
import com.personalcashflow.transaction.TransactionRepository;
import com.personalcashflow.transaction.TransactionStatus;
import com.personalcashflow.transaction.TransactionType;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional
public class ImportService {

    /**
     * The Jan-Aug 2026 history is already fully backfilled from the budget spreadsheet and
     * reconciled against a real bank export (see {@code reconcileOriginalLabels}). A normal import
     * covering that range again would create duplicate transactions, since most of those rows'
     * importHash is still a synthetic backfill id rather than a real bank FITID.
     */
    private static final LocalDate EARLIEST_IMPORTABLE_DATE = LocalDate.of(2026, 9, 1);

    private final ImportBatchRepository importBatchRepository;
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final OfxBankFormat ofxBankFormat;
    private final CsvBankFormat csvBankFormat;

    public ImportService(
            ImportBatchRepository importBatchRepository,
            TransactionRepository transactionRepository,
            AccountRepository accountRepository,
            OfxBankFormat ofxBankFormat,
            CsvBankFormat csvBankFormat) {
        this.importBatchRepository = importBatchRepository;
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.ofxBankFormat = ofxBankFormat;
        this.csvBankFormat = csvBankFormat;
    }

    public ImportSummaryResponse importOfx(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new EmptyImportFileException();
        }

        OfxBankFormat.ParsedStatement statement = parse(file);
        rejectIfTooOld(statement.rows());
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
            if (isDuplicate(accountId, row)) {
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

    /**
     * A CSV export has no equivalent of OFX's ACCTID, so the account is chosen explicitly rather
     * than auto-matched.
     */
    public ImportSummaryResponse importCsv(Long accountId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new EmptyImportFileException();
        }

        List<String> lines = readLines(file, csvBankFormat.charset());
        List<String> dataLines = lines.size() > csvBankFormat.headerLineCount()
                ? lines.subList(csvBankFormat.headerLineCount(), lines.size())
                : List.of();
        List<Optional<ImportedRow>> parsedRows = dataLines.stream()
                .filter(line -> !line.isBlank())
                .map(csvBankFormat::parseLine)
                .toList();
        rejectIfTooOld(parsedRows);

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        int rowCount = parsedRows.size();
        int createdCount = 0;
        int skippedCount = 0;

        for (Optional<ImportedRow> parsed : parsedRows) {
            if (parsed.isEmpty()) {
                skippedCount++;
                continue;
            }

            ImportedRow row = parsed.get();
            if (isDuplicate(accountId, row)) {
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

    /**
     * True if this row's own FITID/hash was already imported, or - catching the case where the same
     * real transaction came in through a different format with an unrelated hash - a transaction on
     * this account already has the same amount, the same original date, and an original label that's
     * a prefix of this one's (or vice versa).
     */
    private boolean isDuplicate(Long accountId, ImportedRow row) {
        if (transactionRepository.existsByAccountIdAndImportHash(accountId, row.fitid())) {
            return true;
        }
        return transactionRepository.existsSimilarByAccountAmountOriginalDateAndLabelPrefix(
                accountId, row.amount(), row.date(), escapeLike(row.label()));
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    @Transactional(readOnly = true)
    public List<ImportSummaryResponse> history() {
        return importBatchRepository.findAllByOrderByImportedAtDesc().stream()
                .map(ImportSummaryResponse::from)
                .toList();
    }

    /**
     * Attaches the bank's real label/date/FITID to already-existing, Excel-backfilled transactions
     * (see {@code Transaction#enrichOriginal}) instead of creating new rows. Matches by account +
     * month + exact amount, widening to the adjacent months when a row's own month has no
     * candidate (the bank's batch-posting date can land a purchase in a different month than the
     * one it was logged under). Safe to re-run on the same file: a row whose FITID is already
     * recorded as some transaction's importHash is skipped.
     */
    public ReconciliationSummaryResponse reconcileOriginalLabels(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new EmptyImportFileException();
        }

        OfxBankFormat.ParsedStatement statement = parse(file);
        Account account = accountRepository.findByAccountNumber(statement.accountNumber())
                .orElseThrow(() -> new UnmatchedOfxAccountException(statement.accountNumber()));
        Long accountId = account.getId();

        List<Optional<ImportedRow>> parsedRows = statement.rows();
        List<ImportedRow> rows = parsedRows.stream().flatMap(Optional::stream).toList();
        Map<YearMonth, List<Transaction>> candidatesByMonth = loadCandidatesByMonth(accountId, rows);

        int matched = 0;
        int alreadyReconciled = 0;
        int ambiguousUnresolved = 0;
        int unmatched = 0;
        List<UnresolvedRow> ambiguousDetails = new ArrayList<>();
        List<UnresolvedRow> unmatchedDetails = new ArrayList<>();

        for (ImportedRow row : rows) {
            if (transactionRepository.existsByAccountIdAndImportHash(accountId, row.fitid())) {
                alreadyReconciled++;
                continue;
            }

            YearMonth rowMonth = YearMonth.from(row.date());
            List<Transaction> pool = matchesByAmount(candidatesByMonth.get(rowMonth), row.amount());
            if (pool.isEmpty()) {
                pool = new ArrayList<>();
                pool.addAll(matchesByAmount(candidatesByMonth.get(rowMonth.minusMonths(1)), row.amount()));
                pool.addAll(matchesByAmount(candidatesByMonth.get(rowMonth.plusMonths(1)), row.amount()));
            }

            if (pool.isEmpty()) {
                unmatched++;
                unmatchedDetails.add(new UnresolvedRow(row.date(), row.label(), row.amount()));
                continue;
            }

            Transaction best = pool.size() == 1 ? pool.get(0) : pickBestByLabel(pool, row.label());
            if (best == null) {
                ambiguousUnresolved++;
                ambiguousDetails.add(new UnresolvedRow(row.date(), row.label(), row.amount()));
                continue;
            }

            best.enrichOriginal(row.date(), row.label(), row.fitid(), row.rawRecord());
            candidatesByMonth.getOrDefault(YearMonth.from(best.getDate()), List.of()).remove(best);
            matched++;
        }

        return new ReconciliationSummaryResponse(
                parsedRows.size(), matched, alreadyReconciled, ambiguousUnresolved, unmatched, ambiguousDetails, unmatchedDetails);
    }

    private Map<YearMonth, List<Transaction>> loadCandidatesByMonth(Long accountId, List<ImportedRow> rows) {
        if (rows.isEmpty()) {
            return Map.of();
        }
        YearMonth minMonth = rows.stream().map(r -> YearMonth.from(r.date())).min(Comparator.naturalOrder()).orElseThrow();
        YearMonth maxMonth = rows.stream().map(r -> YearMonth.from(r.date())).max(Comparator.naturalOrder()).orElseThrow();
        LocalDate from = minMonth.minusMonths(1).atDay(1);
        LocalDate to = maxMonth.plusMonths(1).atEndOfMonth();

        List<Transaction> candidates = transactionRepository.findByAccountIdAndDateBetweenAndStatusAndImportHashStartingWith(
                accountId, from, to, TransactionStatus.POINTEE, "XLSX-");

        Map<YearMonth, List<Transaction>> byMonth = new HashMap<>();
        for (Transaction candidate : candidates) {
            byMonth.computeIfAbsent(YearMonth.from(candidate.getDate()), key -> new ArrayList<>()).add(candidate);
        }
        return byMonth;
    }

    private static List<Transaction> matchesByAmount(List<Transaction> candidates, BigDecimal amount) {
        if (candidates == null) {
            return List.of();
        }
        return candidates.stream().filter(t -> t.getAmount().compareTo(amount) == 0).toList();
    }

    /** Picks the unique best label match; returns null when nothing stands out (a tie, or every candidate scoring zero). */
    private static Transaction pickBestByLabel(List<Transaction> candidates, String bankLabel) {
        double bestScore = -1;
        Transaction best = null;
        int bestCount = 0;
        for (Transaction candidate : candidates) {
            double score = labelSimilarity(candidate.getLabel(), bankLabel);
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
                bestCount = 1;
            } else if (score == bestScore) {
                bestCount++;
            }
        }
        return (bestScore > 0 && bestCount == 1) ? best : null;
    }

    /** Fraction of the existing label's tokens found in the bank label's tokens - simple, dependency-free. */
    private static double labelSimilarity(String existingLabel, String bankLabel) {
        Set<String> existingTokens = normalizeTokens(existingLabel);
        Set<String> bankTokens = normalizeTokens(bankLabel);
        if (existingTokens.isEmpty()) {
            return 0;
        }
        long overlap = existingTokens.stream().filter(bankTokens::contains).count();
        return (double) overlap / existingTokens.size();
    }

    private static Set<String> normalizeTokens(String label) {
        String normalized = Normalizer.normalize(label, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT);
        return Arrays.stream(normalized.split("[^A-Z0-9]+"))
                .filter(token -> token.length() > 1)
                .collect(Collectors.toSet());
    }

    private static void rejectIfTooOld(List<Optional<ImportedRow>> rows) {
        boolean hasOldRow = rows.stream()
                .flatMap(Optional::stream)
                .anyMatch(row -> row.date().isBefore(EARLIEST_IMPORTABLE_DATE));
        if (hasOldRow) {
            throw new ImportTooOldException(EARLIEST_IMPORTABLE_DATE);
        }
    }

    private OfxBankFormat.ParsedStatement parse(MultipartFile file) {
        try (var inputStream = file.getInputStream()) {
            return ofxBankFormat.parse(inputStream);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read uploaded file", e);
        }
    }

    private static List<String> readLines(MultipartFile file, Charset charset) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), charset))) {
            return reader.lines().toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read uploaded file", e);
        }
    }
}
