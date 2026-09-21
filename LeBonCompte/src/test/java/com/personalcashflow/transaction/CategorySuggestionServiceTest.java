package com.personalcashflow.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.personalcashflow.account.Account;
import com.personalcashflow.account.AccountType;
import com.personalcashflow.category.Category;
import com.personalcashflow.category.CategoryRepository;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CategorySuggestionServiceTest {

    private static final Long ACCOUNT_ID = 1L;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private CategorySuggestionService service;

    @Test
    void suggestCategories_withHistoryDifferingOnlyByDate_shouldSuggestCategory() {
        Category courses = categoryWithId(5L, "Courses");
        when(transactionRepository.findCategoryHistoryByAccountIdAndStatus(ACCOUNT_ID, TransactionStatus.POINTEE))
                .thenReturn(List.of(historyRow("ACHAT CB CARREFOUR      05.09.26", 5L)));
        when(categoryRepository.getReferenceById(5L)).thenReturn(courses);

        Transaction newRow = importedRow("ACHAT CB CARREFOUR      07.08.26");
        service.suggestCategories(ACCOUNT_ID, List.of(newRow));

        assertThat(newRow.getCategory()).isEqualTo(courses);
        assertThat(newRow.isCategoryConfirmed()).isFalse();
    }

    @Test
    void suggestCategories_whenTransactionAlreadyHasCategory_shouldNotOverwriteIt() {
        Category original = categoryWithId(1L, "Loisirs et vie sociale");
        Category historyCategory = categoryWithId(5L, "Courses");
        when(transactionRepository.findCategoryHistoryByAccountIdAndStatus(ACCOUNT_ID, TransactionStatus.POINTEE))
                .thenReturn(List.of(historyRow("ACHAT CB CARREFOUR 05.09.26", 5L)));

        Transaction newRow = importedRow("ACHAT CB CARREFOUR 07.08.26");
        newRow.assignCategoryIfMissing(original);

        service.suggestCategories(ACCOUNT_ID, List.of(newRow));

        assertThat(newRow.getCategory()).isEqualTo(original);
        assertThat(newRow.getCategory()).isNotEqualTo(historyCategory);
        assertThat(newRow.isCategoryConfirmed()).isTrue();
    }

    @Test
    void suggestCategories_withNoOriginalLabel_shouldBeSkipped() {
        // non-empty history, so the per-row originalLabel-null guard is what's actually exercised here.
        when(transactionRepository.findCategoryHistoryByAccountIdAndStatus(ACCOUNT_ID, TransactionStatus.POINTEE))
                .thenReturn(List.of(historyRow("ACHAT CB CARREFOUR 05.09.26", 5L)));
        Account account = new Account("Compte courant", AccountType.COURANT, BigDecimal.ZERO, "123");
        Transaction manualRow = Transaction.manual(
                LocalDate.of(2026, 9, 5), "Loyer", BigDecimal.TEN, account, null, TransactionType.DEPENSE);

        service.suggestCategories(ACCOUNT_ID, List.of(manualRow));

        assertThat(manualRow.getCategory()).isNull();
    }

    @Test
    void suggestCategories_withAmbiguousHistory_shouldPickTheMostFrequentCategory() {
        Category frequent = categoryWithId(5L, "Courses");
        when(transactionRepository.findCategoryHistoryByAccountIdAndStatus(ACCOUNT_ID, TransactionStatus.POINTEE))
                .thenReturn(List.of(
                        historyRow("ACHAT CB CARREFOUR 01.09.26", 5L),
                        historyRow("ACHAT CB CARREFOUR 02.09.26", 5L),
                        historyRow("ACHAT CB CARREFOUR 03.09.26", 6L)));
        when(categoryRepository.getReferenceById(5L)).thenReturn(frequent);

        Transaction newRow = importedRow("ACHAT CB CARREFOUR 04.09.26");
        service.suggestCategories(ACCOUNT_ID, List.of(newRow));

        assertThat(newRow.getCategory()).isEqualTo(frequent);
    }

    @Test
    void suggestCategories_whenNewLabelHasExtraCardNumberSuffix_shouldStillMatch() {
        // Real bug: August's "ACHAT CB BOUTIQUE      17.08.26" was categorized, but September's row
        // for the same merchant came back as "ACHAT CB BOUTIQUE      16.09.26 CARTE NUMERO       000" -
        // an exact match on the date-stripped label alone missed this.
        Category courses = categoryWithId(5L, "Courses");
        when(transactionRepository.findCategoryHistoryByAccountIdAndStatus(ACCOUNT_ID, TransactionStatus.POINTEE))
                .thenReturn(List.of(historyRow("ACHAT CB BOUTIQUE           17.08.26", 5L)));
        when(categoryRepository.getReferenceById(5L)).thenReturn(courses);

        Transaction newRow = importedRow("ACHAT CB BOUTIQUE           16.09.26 CARTE NUMERO                000");
        service.suggestCategories(ACCOUNT_ID, List.of(newRow));

        assertThat(newRow.getCategory()).isEqualTo(courses);
    }

    @Test
    void suggestCategories_whenHistoryLabelHasExtraSuffix_shouldStillMatchTheShorterNewLabel() {
        Category courses = categoryWithId(5L, "Courses");
        when(transactionRepository.findCategoryHistoryByAccountIdAndStatus(ACCOUNT_ID, TransactionStatus.POINTEE))
                .thenReturn(List.of(historyRow("ACHAT CB BOUTIQUE 17.08.26 CARTE NUMERO 000", 5L)));
        when(categoryRepository.getReferenceById(5L)).thenReturn(courses);

        Transaction newRow = importedRow("ACHAT CB BOUTIQUE 16.09.26");
        service.suggestCategories(ACCOUNT_ID, List.of(newRow));

        assertThat(newRow.getCategory()).isEqualTo(courses);
    }

    @Test
    void suggestCategories_withNoMatchingHistory_shouldLeaveUncategorized() {
        when(transactionRepository.findCategoryHistoryByAccountIdAndStatus(any(), any())).thenReturn(List.of());

        Transaction newRow = importedRow("ACHAT CB INCONNU 05.09.26");
        service.suggestCategories(ACCOUNT_ID, List.of(newRow));

        assertThat(newRow.getCategory()).isNull();
    }

    private static Transaction importedRow(String originalLabel) {
        Account account = new Account("Compte courant", AccountType.COURANT, BigDecimal.ZERO, "123");
        return Transaction.imported(
                LocalDate.of(2026, 9, 5), originalLabel, BigDecimal.TEN, account, TransactionType.DEPENSE, "hash", "raw");
    }

    private static LabelCategoryProjection historyRow(String originalLabel, Long categoryId) {
        return new LabelCategoryProjection() {
            @Override
            public String getOriginalLabel() {
                return originalLabel;
            }

            @Override
            public Long getCategoryId() {
                return categoryId;
            }
        };
    }

    private static Category categoryWithId(Long id, String name) {
        Category category = new Category(name, null);
        try {
            Field idField = Category.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(category, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        return category;
    }
}
