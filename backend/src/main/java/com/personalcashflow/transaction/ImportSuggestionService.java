package com.personalcashflow.transaction;

import com.personalcashflow.category.Category;
import com.personalcashflow.category.CategoryRepository;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Guesses a category and a friendlier label for a freshly-imported pointée from this account's
 * own history: transactions whose bank label (once dates are stripped out, since a merchant's
 * label otherwise differs on every occurrence - e.g. "ACHAT CB CARREFOUR      05.09.26" vs
 * "...07.08.26") match a label this account has already seen before. Labels only need to
 * prefix-match each other rather than be identical, since the bank sometimes appends extra detail
 * later occurrences didn't have (e.g. "ACHAT CB BOUTIQUE" one month, "ACHAT CB BOUTIQUE CARTE NUMERO 000"
 * the next) - the same "one truncates the other" idea as {@link
 * TransactionRepository#existsSimilarByAccountAmountOriginalDateAndLabelPrefix}. When several past
 * occurrences disagree, the most frequent category/label wins. A guess is only ever offered, never
 * forced: see {@link Transaction#suggestCategory}/{@link Transaction#suggestLabel}, which no-op if
 * a value is already set/customized, and {@link Transaction#isCategoryConfirmed}/{@link
 * Transaction#isLabelConfirmed} for how the caller is expected to surface them as unconfirmed.
 */
@Service
@Transactional(readOnly = true)
public class ImportSuggestionService {

    private static final Pattern DATE_PATTERN = Pattern.compile("\\d{1,2}[./]\\d{1,2}[./]\\d{2,4}");

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;

    public ImportSuggestionService(TransactionRepository transactionRepository, CategoryRepository categoryRepository) {
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
    }

    private record HistoryEntry(String normalizedLabel, String originalLabel, String label, Long categoryId) {
    }

    /** Mutates the given (not-yet-persisted or managed) transactions in place; the caller still needs to save them. */
    public void suggest(Long accountId, List<Transaction> transactions) {
        List<HistoryEntry> history = buildHistory(accountId);
        if (history.isEmpty()) {
            return;
        }

        Map<Long, Category> categoryCache = new HashMap<>();
        for (Transaction transaction : transactions) {
            if (transaction.getOriginalLabel() == null) {
                continue;
            }
            List<HistoryEntry> matches = matchingEntries(normalize(transaction.getOriginalLabel()), history);
            if (matches.isEmpty()) {
                continue;
            }

            if (transaction.getCategory() == null) {
                bestCategory(matches).ifPresent(categoryId -> {
                    Category category = categoryCache.computeIfAbsent(categoryId, categoryRepository::getReferenceById);
                    transaction.suggestCategory(category);
                });
            }
            bestLabel(matches).ifPresent(transaction::suggestLabel);
        }
    }

    private List<HistoryEntry> buildHistory(Long accountId) {
        return transactionRepository.findSuggestionHistoryByAccountIdAndStatus(accountId, TransactionStatus.POINTEE).stream()
                .map(row -> new HistoryEntry(normalize(row.getOriginalLabel()), row.getOriginalLabel(), row.getLabel(), row.getCategoryId()))
                // An empty normalized label (e.g. the original was nothing but a date) would
                // otherwise prefix-match every possible label.
                .filter(entry -> !entry.normalizedLabel().isEmpty())
                .toList();
    }

    /** Every history entry that prefix-matches the new row's label (either direction). */
    private static List<HistoryEntry> matchingEntries(String normalizedLabel, List<HistoryEntry> history) {
        if (normalizedLabel.isEmpty()) {
            return List.of();
        }
        List<HistoryEntry> matches = new ArrayList<>();
        for (HistoryEntry entry : history) {
            if (entry.normalizedLabel().startsWith(normalizedLabel) || normalizedLabel.startsWith(entry.normalizedLabel())) {
                matches.add(entry);
            }
        }
        return matches;
    }

    private static Optional<Long> bestCategory(List<HistoryEntry> matches) {
        Map<Long, Integer> counts = new HashMap<>();
        for (HistoryEntry entry : matches) {
            if (entry.categoryId() != null) {
                counts.merge(entry.categoryId(), 1, Integer::sum);
            }
        }
        return counts.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey);
    }

    /** Only counts entries the user (or an earlier suggestion) actually renamed away from the bank's raw wording. */
    private static Optional<String> bestLabel(List<HistoryEntry> matches) {
        Map<String, Integer> counts = new HashMap<>();
        for (HistoryEntry entry : matches) {
            if (!entry.label().equals(entry.originalLabel())) {
                counts.merge(entry.label(), 1, Integer::sum);
            }
        }
        return counts.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey);
    }

    /** Strips embedded dates, accents and casing/whitespace noise so the same merchant matches across occurrences. */
    private static String normalize(String label) {
        String withoutDates = DATE_PATTERN.matcher(label).replaceAll(" ");
        String withoutAccents = Normalizer.normalize(withoutDates, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return withoutAccents.toUpperCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }
}
