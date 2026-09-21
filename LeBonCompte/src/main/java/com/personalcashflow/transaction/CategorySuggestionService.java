package com.personalcashflow.transaction;

import com.personalcashflow.category.Category;
import com.personalcashflow.category.CategoryRepository;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Guesses a category for a freshly-imported pointée from this account's own history: transactions
 * whose bank label (once dates are stripped out, since a merchant's label otherwise differs on
 * every occurrence - e.g. "ACHAT CB CARREFOUR      05.09.26" vs "...07.08.26") match a label this
 * account has already categorized before. Labels only need to prefix-match each other rather than
 * be identical, since the bank sometimes appends extra detail later occurrences didn't have (e.g.
 * "ACHAT CB BOUTIQUE" one month, "ACHAT CB BOUTIQUE CARTE NUMERO 000" the next) - the same "one truncates
 * the other" idea as {@link TransactionRepository#existsSimilarByAccountAmountOriginalDateAndLabelPrefix}.
 * When several past occurrences disagree, the most frequent category wins. A guess is only ever
 * offered, never forced: see {@link Transaction#suggestCategory}, which no-ops if a category is
 * already set, and {@link Transaction#isCategoryConfirmed} for how the caller is expected to
 * surface it as unconfirmed.
 */
@Service
@Transactional(readOnly = true)
public class CategorySuggestionService {

    private static final Pattern DATE_PATTERN = Pattern.compile("\\d{1,2}[./]\\d{1,2}[./]\\d{2,4}");

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;

    public CategorySuggestionService(TransactionRepository transactionRepository, CategoryRepository categoryRepository) {
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
    }

    private record HistoryEntry(String normalizedLabel, Long categoryId) {
    }

    /** Mutates the given (not-yet-persisted or managed) transactions in place; the caller still needs to save them. */
    public void suggestCategories(Long accountId, List<Transaction> transactions) {
        List<HistoryEntry> history = buildHistory(accountId);
        if (history.isEmpty()) {
            return;
        }

        Map<Long, Category> categoryCache = new HashMap<>();
        for (Transaction transaction : transactions) {
            if (transaction.getCategory() != null || transaction.getOriginalLabel() == null) {
                continue;
            }
            String normalizedLabel = normalize(transaction.getOriginalLabel());
            bestMatchingCategory(normalizedLabel, history).ifPresent(categoryId -> {
                Category category = categoryCache.computeIfAbsent(categoryId, categoryRepository::getReferenceById);
                transaction.suggestCategory(category);
            });
        }
    }

    private List<HistoryEntry> buildHistory(Long accountId) {
        return transactionRepository.findCategoryHistoryByAccountIdAndStatus(accountId, TransactionStatus.POINTEE).stream()
                .map(row -> new HistoryEntry(normalize(row.getOriginalLabel()), row.getCategoryId()))
                // An empty normalized label (e.g. the original was nothing but a date) would
                // otherwise prefix-match every possible label.
                .filter(entry -> !entry.normalizedLabel().isEmpty())
                .toList();
    }

    /** Tallies the category of every history entry that prefix-matches (either direction), picks the most frequent. */
    private static Optional<Long> bestMatchingCategory(String normalizedLabel, List<HistoryEntry> history) {
        if (normalizedLabel.isEmpty()) {
            return Optional.empty();
        }
        Map<Long, Integer> counts = new HashMap<>();
        for (HistoryEntry entry : history) {
            if (entry.normalizedLabel().startsWith(normalizedLabel) || normalizedLabel.startsWith(entry.normalizedLabel())) {
                counts.merge(entry.categoryId(), 1, Integer::sum);
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
