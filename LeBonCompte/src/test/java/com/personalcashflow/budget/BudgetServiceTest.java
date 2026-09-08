package com.personalcashflow.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
        when(budgetRepository.findByMonth(LocalDate.of(2026, 9, 1))).thenReturn(List.of(budget));
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
    void create_whenBudgetAlreadyExistsForCategoryAndMonth_shouldThrowConflict() {
        Category category = categoryWithId(1L, "Courses alimentaires");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(budgetRepository.existsByCategoryIdAndMonth(1L, LocalDate.of(2026, 9, 1))).thenReturn(true);

        BudgetRequest request = new BudgetRequest(1L, "2026-09", BigDecimal.valueOf(500));

        assertThatThrownBy(() -> budgetService.create(request))
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
