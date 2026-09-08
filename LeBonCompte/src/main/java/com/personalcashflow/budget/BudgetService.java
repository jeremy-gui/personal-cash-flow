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

    @Transactional(readOnly = true)
    public List<BudgetResponse> findAllForMonth(String month) {
        YearMonth yearMonth = YearMonth.parse(month);
        LocalDate monthStart = yearMonth.atDay(1);
        LocalDate monthEnd = yearMonth.atEndOfMonth();

        return budgetRepository.findByMonth(monthStart).stream()
                .map(budget -> BudgetResponse.of(budget, spent(budget.getCategory().getId(), monthStart, monthEnd)))
                .toList();
    }

    public BudgetResponse create(BudgetRequest request) {
        Category category = resolveCategory(request.categoryId());
        LocalDate monthStart = YearMonth.parse(request.month()).atDay(1);
        if (budgetRepository.existsByCategoryIdAndMonth(category.getId(), monthStart)) {
            throw new BudgetAlreadyExistsException(category.getId(), request.month());
        }
        Budget budget = new Budget(category, monthStart, request.amount());
        budgetRepository.save(budget);
        return BudgetResponse.of(budget, spentForMonth(category.getId(), monthStart));
    }

    public BudgetResponse update(Long id, BudgetRequest request) {
        Budget budget = findById(id);
        Category category = resolveCategory(request.categoryId());
        LocalDate monthStart = YearMonth.parse(request.month()).atDay(1);
        if (budgetRepository.existsByCategoryIdAndMonthAndIdNot(category.getId(), monthStart, id)) {
            throw new BudgetAlreadyExistsException(category.getId(), request.month());
        }
        budget.update(category, monthStart, request.amount());
        return BudgetResponse.of(budget, spentForMonth(category.getId(), monthStart));
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
