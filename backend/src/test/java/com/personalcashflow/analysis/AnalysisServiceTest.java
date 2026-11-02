package com.personalcashflow.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.personalcashflow.analysis.dto.YearlyAnalysisResponse;
import com.personalcashflow.budget.BudgetService;
import com.personalcashflow.budget.dto.BudgetResponse;
import com.personalcashflow.transaction.CategorySpendingProjection;
import com.personalcashflow.transaction.TransactionRepository;
import com.personalcashflow.transaction.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnalysisServiceTest {

    private static final int PAST_YEAR = YearMonth.now().getYear() - 1;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private BudgetService budgetService;

    @InjectMocks
    private AnalysisService analysisService;

    @Test
    void getYearlyAnalysis_forAFullyElapsedYear_shouldSumAcrossAllTwelveMonthsAndAllAccounts() {
        when(transactionRepository.sumByTypeAndDateRangeAllAccounts(eq(TransactionType.REVENU), any(), any()))
                .thenReturn(BigDecimal.valueOf(4500));
        when(transactionRepository.sumByTypeAndDateRangeAllAccounts(eq(TransactionType.DEPENSE), any(), any()))
                .thenReturn(BigDecimal.valueOf(-3500));
        when(transactionRepository.sumSavingsContributions(any(), any())).thenReturn(BigDecimal.valueOf(1000));
        when(transactionRepository.sumSavingsWithdrawals(any(), any())).thenReturn(BigDecimal.valueOf(-50));
        stubEmptyBreakdowns();
        when(budgetService.findAllForMonth(YearMonth.of(PAST_YEAR, 12).toString())).thenReturn(List.of());

        YearlyAnalysisResponse response = analysisService.getYearlyAnalysis(PAST_YEAR);

        assertThat(response.monthlyResults()).hasSize(12);
        assertThat(response.totalRevenus()).isEqualByComparingTo(BigDecimal.valueOf(54000));
        assertThat(response.totalDepenses()).isEqualByComparingTo(BigDecimal.valueOf(42000));
        assertThat(response.totalResultat()).isEqualByComparingTo(BigDecimal.valueOf(12000));
        assertThat(response.totalEpargne()).isEqualByComparingTo(BigDecimal.valueOf(12000));
        assertThat(response.monthlyResults().get(0).epargneUtilisee()).isEqualByComparingTo(BigDecimal.valueOf(50));
        assertThat(response.totalEpargneUtilisee()).isEqualByComparingTo(BigDecimal.valueOf(600));
        assertThat(response.savingsRate()).isEqualByComparingTo(BigDecimal.valueOf(22.2));
    }

    @Test
    void getYearlyAnalysis_epargneMustComeFromSavingsContributionsNotFromRevenusMinusDepenses() {
        when(transactionRepository.sumByTypeAndDateRangeAllAccounts(eq(TransactionType.REVENU), any(), any()))
                .thenReturn(BigDecimal.valueOf(4500));
        when(transactionRepository.sumByTypeAndDateRangeAllAccounts(eq(TransactionType.DEPENSE), any(), any()))
                .thenReturn(BigDecimal.valueOf(-4500));
        when(transactionRepository.sumSavingsContributions(any(), any())).thenReturn(BigDecimal.valueOf(200));
        when(transactionRepository.sumSavingsWithdrawals(any(), any())).thenReturn(BigDecimal.ZERO);
        stubEmptyBreakdowns();
        when(budgetService.findAllForMonth(YearMonth.of(PAST_YEAR, 12).toString())).thenReturn(List.of());

        YearlyAnalysisResponse response = analysisService.getYearlyAnalysis(PAST_YEAR);

        assertThat(response.totalResultat()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.totalEpargne()).isEqualByComparingTo(BigDecimal.valueOf(2400));
    }

    @Test
    void getYearlyAnalysis_shouldCompareCategoryAgainstLatestElapsedMonthBudget() {
        when(transactionRepository.sumByTypeAndDateRangeAllAccounts(any(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(transactionRepository.sumSavingsContributions(any(), any())).thenReturn(BigDecimal.ZERO);
        when(transactionRepository.sumSavingsWithdrawals(any(), any())).thenReturn(BigDecimal.ZERO);
        when(transactionRepository.sumDepenseGroupedByCategoryAllAccounts(
                        LocalDate.of(PAST_YEAR, 1, 1), LocalDate.of(PAST_YEAR, 12, 31)))
                .thenReturn(List.of(categorySpending(1L, "Courses", BigDecimal.valueOf(-600))));
        when(transactionRepository.sumDepenseGroupedByMonthAndCategoryAllAccounts(any(), any())).thenReturn(List.of());
        when(transactionRepository.sumSavingsContributionsGroupedByAccount(any(), any())).thenReturn(List.of());
        BudgetResponse budget = new BudgetResponse(
                1L, 1L, "Courses", "2026-12", BigDecimal.valueOf(500),
                BigDecimal.valueOf(50), BigDecimal.valueOf(450), BigDecimal.valueOf(10));
        when(budgetService.findAllForMonth(YearMonth.of(PAST_YEAR, 12).toString())).thenReturn(List.of(budget));

        YearlyAnalysisResponse response = analysisService.getYearlyAnalysis(PAST_YEAR);

        assertThat(response.categoryBreakdown()).hasSize(1);
        var category = response.categoryBreakdown().get(0);
        assertThat(category.amount()).isEqualByComparingTo(BigDecimal.valueOf(600));
        assertThat(category.currentMonthBudget()).isEqualByComparingTo(BigDecimal.valueOf(500));
        assertThat(category.currentMonthActual()).isEqualByComparingTo(BigDecimal.valueOf(50));
        assertThat(category.currentMonthEcart()).isEqualByComparingTo(BigDecimal.valueOf(-450));
    }

    private void stubEmptyBreakdowns() {
        when(transactionRepository.sumDepenseGroupedByCategoryAllAccounts(any(), any())).thenReturn(List.of());
        when(transactionRepository.sumDepenseGroupedByMonthAndCategoryAllAccounts(any(), any())).thenReturn(List.of());
        when(transactionRepository.sumSavingsContributionsGroupedByAccount(any(), any())).thenReturn(List.of());
    }

    private static CategorySpendingProjection categorySpending(Long id, String name, BigDecimal total) {
        return new CategorySpendingProjection() {
            @Override
            public Long getCategoryId() {
                return id;
            }

            @Override
            public String getCategoryName() {
                return name;
            }

            @Override
            public BigDecimal getTotal() {
                return total;
            }
        };
    }
}
