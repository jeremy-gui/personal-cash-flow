package com.personalcashflow.budget;

import com.personalcashflow.budget.dto.BudgetRequest;
import com.personalcashflow.budget.dto.BudgetResponse;
import com.personalcashflow.category.Category;
import com.personalcashflow.category.CategoryNotFoundException;
import com.personalcashflow.category.CategoryRepository;
import com.personalcashflow.transaction.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;

    public BudgetService(
            BudgetRepository budgetRepository,
            CategoryRepository categoryRepository,
            TransactionRepository transactionRepository) {
        this.budgetRepository = budgetRepository;
        this.categoryRepository = categoryRepository;
        this.transactionRepository = transactionRepository;
    }

    /**
     * A budget row applies from its month onward until a later row for the same category
     * supersedes it, so this resolves - per category - the latest row whose month is at or
     * before {@code month}. Household-scale data: reducing in Java is simpler than a window
     * function and this table will never be large enough to matter.
     */
    @Transactional(readOnly = true)
    public List<BudgetResponse> findAllForMonth(String month) {
        YearMonth yearMonth = YearMonth.parse(month);
        LocalDate monthStart = yearMonth.atDay(1);
        LocalDate monthEnd = yearMonth.atEndOfMonth();

        var latestPerCategory = budgetRepository.findByMonthLessThanEqual(monthStart).stream()
                .collect(Collectors.toMap(
                        b -> b.getCategory().getId(),
                        b -> b,
                        (a, b) -> a.getMonth().isAfter(b.getMonth()) ? a : b));

        return latestPerCategory.values().stream()
                .map(budget -> BudgetResponse.of(budget, spent(budget.getCategory().getId(), monthStart, monthEnd)))
                .toList();
    }

    public BudgetResponse create(BudgetRequest request) {
        Category category = resolveCategory(request.categoryId());
        LocalDate monthStart = YearMonth.parse(request.month()).atDay(1);
        if (budgetRepository.existsByCategoryIdAndMonth(category.getId(), monthStart)) {
            throw new BudgetAlreadyExistsException(category.getId());
        }
        Budget budget = new Budget(category, monthStart, request.amount());
        budgetRepository.save(budget);
        return BudgetResponse.of(budget, spentForMonth(category.getId(), monthStart));
    }

    public BudgetResponse update(Long id, BudgetRequest request) {
        Budget budget = findById(id);
        Category category = resolveCategory(request.categoryId());
        if (budgetRepository.existsByCategoryIdAndMonthAndIdNot(category.getId(), budget.getMonth(), id)) {
            throw new BudgetAlreadyExistsException(category.getId());
        }
        budget.update(category, request.amount());
        return BudgetResponse.of(budget, spentForMonth(category.getId(), budget.getMonth()));
    }

    public void delete(Long id) {
        Budget budget = findById(id);
        budgetRepository.delete(budget);
    }

    private Budget findById(Long id) {
        return budgetRepository.findById(id)
                .orElseThrow(() -> new BudgetNotFoundException(id));
    }

    private Category resolveCategory(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CategoryNotFoundException(categoryId));
    }

    private BigDecimal spentForMonth(Long categoryId, LocalDate monthStart) {
        return spent(categoryId, monthStart, YearMonth.from(monthStart).atEndOfMonth());
    }

    private BigDecimal spent(Long categoryId, LocalDate from, LocalDate to) {
        return transactionRepository.sumDepenseByCategoryAndDateRange(categoryId, from, to).negate();
    }
}
