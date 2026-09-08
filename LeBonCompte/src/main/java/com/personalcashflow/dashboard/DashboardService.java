package com.personalcashflow.dashboard;

import com.personalcashflow.account.AccountRepository;
import com.personalcashflow.budget.BudgetService;
import com.personalcashflow.dashboard.dto.CategoryAmountResponse;
import com.personalcashflow.dashboard.dto.DashboardResponse;
import com.personalcashflow.transaction.TransactionRepository;
import com.personalcashflow.transaction.TransactionService;
import com.personalcashflow.transaction.TransactionStatus;
import com.personalcashflow.transaction.TransactionType;
import com.personalcashflow.transaction.dto.TransactionResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private final TransactionRepository transactionRepository;
    private final TransactionService transactionService;
    private final BudgetService budgetService;
    private final AccountRepository accountRepository;

    public DashboardService(
            TransactionRepository transactionRepository,
            TransactionService transactionService,
            BudgetService budgetService,
            AccountRepository accountRepository) {
        this.transactionRepository = transactionRepository;
        this.transactionService = transactionService;
        this.budgetService = budgetService;
        this.accountRepository = accountRepository;
    }

    public DashboardResponse getDashboard(String month) {
        YearMonth yearMonth = YearMonth.parse(month);
        LocalDate from = yearMonth.atDay(1);
        LocalDate to = yearMonth.atEndOfMonth();

        BigDecimal revenus = transactionRepository.sumByTypeAndDateRange(TransactionType.REVENU, from, to);
        BigDecimal depensesSigned = transactionRepository.sumByTypeAndDateRange(TransactionType.DEPENSE, from, to);
        BigDecimal depenses = depensesSigned.negate();
        BigDecimal soldeNet = revenus.add(depensesSigned);

        var depensesParCategorie = transactionRepository.sumDepenseGroupedByCategory(from, to).stream()
                .map(p -> new CategoryAmountResponse(p.getCategoryId(), p.getCategoryName(), p.getTotal().negate()))
                .toList();

        var budgetsParCategorie = budgetService.findAllForMonth(month);

        var previsionnellesNonPointees = transactionService
                .search(month, null, null, TransactionStatus.PREVISIONNELLE, null)
                .stream()
                .map(TransactionResponse::from)
                .toList();

        BigDecimal soldeActuel = accountRepository.sumInitialBalance()
                .add(transactionRepository.sumByStatus(TransactionStatus.POINTEE));
        BigDecimal previsionnelFinDeMois = soldeActuel
                .add(transactionRepository.sumByStatusUpToDate(TransactionStatus.PREVISIONNELLE, to));

        return new DashboardResponse(
                month,
                revenus,
                depenses,
                soldeNet,
                soldeActuel,
                previsionnelFinDeMois,
                depensesParCategorie,
                budgetsParCategorie,
                previsionnellesNonPointees);
    }
}
