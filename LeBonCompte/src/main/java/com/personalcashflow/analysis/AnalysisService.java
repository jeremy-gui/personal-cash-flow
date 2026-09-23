package com.personalcashflow.analysis;

import com.personalcashflow.analysis.dto.CategoryYearSummaryResponse;
import com.personalcashflow.analysis.dto.MonthlyCategoryAmountResponse;
import com.personalcashflow.analysis.dto.MonthlyResultResponse;
import com.personalcashflow.analysis.dto.SavingsAccountSummaryResponse;
import com.personalcashflow.analysis.dto.YearlyAnalysisResponse;
import com.personalcashflow.budget.BudgetService;
import com.personalcashflow.budget.dto.BudgetResponse;
import com.personalcashflow.transaction.TransactionRepository;
import com.personalcashflow.transaction.TransactionType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Household-wide: every figure spans all accounts, not just the default one - see YearlyAnalysisResponse. */
@Service
@Transactional(readOnly = true)
public class AnalysisService {

    private final TransactionRepository transactionRepository;
    private final BudgetService budgetService;

    public AnalysisService(TransactionRepository transactionRepository, BudgetService budgetService) {
        this.transactionRepository = transactionRepository;
        this.budgetService = budgetService;
    }

    public YearlyAnalysisResponse getYearlyAnalysis(int year) {
        LocalDate from = LocalDate.of(year, 1, 1);
        LocalDate to = LocalDate.of(year, 12, 31);
        int monthsElapsed = monthsElapsed(year);

        List<MonthlyResultResponse> monthlyResults = new ArrayList<>();
        BigDecimal totalRevenus = BigDecimal.ZERO;
        BigDecimal totalDepenses = BigDecimal.ZERO;
        BigDecimal totalEpargne = BigDecimal.ZERO;

        for (int month = 1; month <= monthsElapsed; month++) {
            YearMonth yearMonth = YearMonth.of(year, month);
            LocalDate monthFrom = yearMonth.atDay(1);
            LocalDate monthTo = yearMonth.atEndOfMonth();

            BigDecimal revenus = transactionRepository.sumByTypeAndDateRangeAllAccounts(
                    TransactionType.REVENU, monthFrom, monthTo);
            BigDecimal depensesSigned = transactionRepository.sumByTypeAndDateRangeAllAccounts(
                    TransactionType.DEPENSE, monthFrom, monthTo);
            BigDecimal depenses = depensesSigned.negate();
            BigDecimal epargne = transactionRepository.sumSavingsContributions(monthFrom, monthTo);
            BigDecimal resultat = revenus.add(depensesSigned);

            monthlyResults.add(new MonthlyResultResponse(month, revenus, depenses, resultat, epargne));
            totalRevenus = totalRevenus.add(revenus);
            totalDepenses = totalDepenses.add(depenses);
            totalEpargne = totalEpargne.add(epargne);
        }

        BigDecimal totalResultat = totalRevenus.subtract(totalDepenses);
        BigDecimal savingsRate = percentage(totalEpargne, totalRevenus);

        BigDecimal yearRevenus = totalRevenus;
        Map<Long, BudgetResponse> latestMonthBudgets = monthsElapsed > 0
                ? budgetService.findAllForMonth(YearMonth.of(year, monthsElapsed).toString()).stream()
                        .collect(Collectors.toMap(BudgetResponse::categoryId, b -> b))
                : Map.of();

        List<CategoryYearSummaryResponse> categoryBreakdown = transactionRepository
                .sumDepenseGroupedByCategoryAllAccounts(from, to).stream()
                .map(p -> {
                    BigDecimal amount = p.getTotal().negate();
                    BudgetResponse budget = latestMonthBudgets.get(p.getCategoryId());
                    return new CategoryYearSummaryResponse(
                            p.getCategoryId(),
                            p.getCategoryName(),
                            amount,
                            percentage(amount, yearRevenus),
                            budget != null ? budget.amount() : null,
                            budget != null ? budget.spent() : null,
                            budget != null ? budget.spent().subtract(budget.amount()) : null);
                })
                .toList();

        List<MonthlyCategoryAmountResponse> evolutionParCategorie = transactionRepository
                .sumDepenseGroupedByMonthAndCategoryAllAccounts(from, to).stream()
                .map(p -> new MonthlyCategoryAmountResponse(p.getMonth(), p.getCategoryId(), p.getCategoryName(), p.getTotal().negate()))
                .toList();

        List<SavingsAccountSummaryResponse> epargneParCompte = transactionRepository
                .sumSavingsContributionsGroupedByAccount(from, to).stream()
                .map(p -> new SavingsAccountSummaryResponse(p.getAccountId(), p.getAccountName(), p.getTotal()))
                .toList();

        return new YearlyAnalysisResponse(
                year,
                monthlyResults,
                totalRevenus,
                totalDepenses,
                totalResultat,
                totalEpargne,
                savingsRate,
                categoryBreakdown,
                evolutionParCategorie,
                epargneParCompte);
    }

    /** A future year has no data yet; a past year averages/sums over all 12 months; the current year only over the months so far. */
    private static int monthsElapsed(int year) {
        int currentYear = YearMonth.now().getYear();
        if (year < currentYear) {
            return 12;
        }
        if (year == currentYear) {
            return YearMonth.now().getMonthValue();
        }
        return 0;
    }

    private static BigDecimal percentage(BigDecimal part, BigDecimal whole) {
        if (whole.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return part.multiply(BigDecimal.valueOf(100)).divide(whole, 1, RoundingMode.HALF_UP);
    }
}
