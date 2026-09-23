package com.personalcashflow.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.personalcashflow.budget.dto.BudgetRequest;
import com.personalcashflow.category.Category;
import com.personalcashflow.category.CategoryNotFoundException;
import com.personalcashflow.category.CategoryRepository;
import com.personalcashflow.transaction.TransactionRepository;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BudgetServiceTest {

    @Mock
    private BudgetRepository budgetRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private BudgetService budgetService;

    @Test
    void findAllForMonth_shouldComputeSpentRemainingAndPercentage() {
        Category category = categoryWithId(1L, "Courses alimentaires");
        Budget budget = new Budget(category, LocalDate.of(2026, 9, 1), BigDecimal.valueOf(500));
        when(budgetRepository.findByMonthLessThanEqual(LocalDate.of(2026, 9, 1))).thenReturn(List.of(budget));
        when(transactionRepository.sumDepenseByCategoryAndDateRange(
                1L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
                .thenReturn(BigDecimal.valueOf(-150));

        var responses = budgetService.findAllForMonth("2026-09");

        assertThat(responses).hasSize(1);
        var response = responses.get(0);
        assertThat(response.spent()).isEqualByComparingTo(BigDecimal.valueOf(150));
        assertThat(response.remaining()).isEqualByComparingTo(BigDecimal.valueOf(350));
        assertThat(response.percentageConsumed()).isEqualByComparingTo(BigDecimal.valueOf(30.0));
    }

    @Test
    void findAllForMonth_shouldIncludeBudgetsStartedInEarlierMonths() {
        Category category = categoryWithId(1L, "Courses alimentaires");
        Budget budget = new Budget(category, LocalDate.of(2026, 7, 1), BigDecimal.valueOf(500));
        when(budgetRepository.findByMonthLessThanEqual(LocalDate.of(2026, 9, 1))).thenReturn(List.of(budget));
        when(transactionRepository.sumDepenseByCategoryAndDateRange(
                1L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
                .thenReturn(BigDecimal.ZERO);

        var responses = budgetService.findAllForMonth("2026-09");

        assertThat(responses).hasSize(1);
    }

    @Test
    void findAllForMonth_whenSeveralMonthsExistForACategory_shouldResolveOnlyTheLatestOne() {
        Category category = categoryWithId(1L, "Courses alimentaires");
        Budget july = new Budget(category, LocalDate.of(2026, 7, 1), BigDecimal.valueOf(400));
        Budget september = new Budget(category, LocalDate.of(2026, 9, 1), BigDecimal.valueOf(500));
        // Order deliberately not sorted, to prove resolution doesn't rely on repository ordering.
        when(budgetRepository.findByMonthLessThanEqual(LocalDate.of(2026, 9, 1)))
                .thenReturn(List.of(september, july));
        when(transactionRepository.sumDepenseByCategoryAndDateRange(
                1L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
                .thenReturn(BigDecimal.ZERO);

        var responses = budgetService.findAllForMonth("2026-09");

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).amount()).isEqualByComparingTo(BigDecimal.valueOf(500));
        assertThat(responses.get(0).month()).isEqualTo("2026-09");
    }

    @Test
    void create_whenBudgetAlreadyExistsForCategoryAndMonth_shouldThrowConflict() {
        Category category = categoryWithId(1L, "Courses alimentaires");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(budgetRepository.existsByCategoryIdAndMonth(1L, LocalDate.of(2026, 9, 1))).thenReturn(true);

        BudgetRequest request = new BudgetRequest(1L, "2026-09", BigDecimal.valueOf(500));

        assertThatThrownBy(() -> budgetService.create(request))
                .isInstanceOf(BudgetAlreadyExistsException.class);
    }

    @Test
    void create_withANewMonthForACategoryThatAlreadyHasOtherMonths_shouldNotConflict() {
        Category category = categoryWithId(1L, "Courses alimentaires");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(budgetRepository.existsByCategoryIdAndMonth(1L, LocalDate.of(2026, 10, 1))).thenReturn(false);
        when(budgetRepository.save(any(Budget.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionRepository.sumDepenseByCategoryAndDateRange(
                1L, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)))
                .thenReturn(BigDecimal.ZERO);

        BudgetRequest request = new BudgetRequest(1L, "2026-10", BigDecimal.valueOf(600));

        var response = budgetService.create(request);

        assertThat(response.month()).isEqualTo("2026-10");
        assertThat(response.amount()).isEqualByComparingTo(BigDecimal.valueOf(600));
    }

    @Test
    void update_whenAnotherRowAlreadyExistsForTheSameCategoryAndMonth_shouldThrowConflict() {
        Category category = categoryWithId(1L, "Courses alimentaires");
        Category otherCategory = categoryWithId(2L, "Loisirs");
        Budget budget = budgetWithId(10L, category, LocalDate.of(2026, 9, 1), BigDecimal.valueOf(500));
        when(budgetRepository.findById(10L)).thenReturn(Optional.of(budget));
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(otherCategory));
        when(budgetRepository.existsByCategoryIdAndMonthAndIdNot(2L, LocalDate.of(2026, 9, 1), 10L))
                .thenReturn(true);

        BudgetRequest request = new BudgetRequest(2L, "2026-09", BigDecimal.valueOf(500));

        assertThatThrownBy(() -> budgetService.update(10L, request))
                .isInstanceOf(BudgetAlreadyExistsException.class);
    }

    @Test
    void create_whenCategoryMissing_shouldThrowNotFound() {
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        BudgetRequest request = new BudgetRequest(99L, "2026-09", BigDecimal.valueOf(500));

        assertThatThrownBy(() -> budgetService.create(request))
                .isInstanceOf(CategoryNotFoundException.class);
    }

    @Test
    void delete_whenBudgetMissing_shouldThrowNotFound() {
        when(budgetRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> budgetService.delete(42L))
                .isInstanceOf(BudgetNotFoundException.class);
    }

    private static Budget budgetWithId(Long id, Category category, LocalDate month, BigDecimal amount) {
        Budget budget = new Budget(category, month, amount);
        try {
            Field idField = Budget.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(budget, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        return budget;
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
