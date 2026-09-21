package com.personalcashflow.dashboard;

import com.personalcashflow.account.Account;
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
        Account defaultAccount = accountRepository.findByIsDefaultTrue()
                .orElseThrow(NoDefaultAccountException::new);
        Long accountId = defaultAccount.getId();

        YearMonth yearMonth = YearMonth.parse(month);
        LocalDate from = yearMonth.atDay(1);
        LocalDate to = yearMonth.atEndOfMonth();

        BigDecimal revenus = transactionRepository.sumByTypeAndDateRange(TransactionType.REVENU, accountId, from, to);
        BigDecimal depensesSigned =
                transactionRepository.sumByTypeAndDateRange(TransactionType.DEPENSE, accountId, from, to);
        BigDecimal depenses = depensesSigned.negate();
        BigDecimal soldeNet = revenus.add(depensesSigned);

        var depensesParCategorie = transactionRepository.sumDepenseGroupedByCategory(accountId, from, to).stream()
                .map(p -> new CategoryAmountResponse(p.getCategoryId(), p.getCategoryName(), p.getTotal().negate()))
                .toList();

        // Budgets stay household-wide: Budget has no account relation.
        var budgetsParCategorie = budgetService.findAllForMonth(month);

        var previsionnellesNonPointees = transactionService
                .search(month, accountId, null, TransactionStatus.PREVISIONNELLE, null)
                .stream()
                .map(TransactionResponse::from)
                .toList();

        BigDecimal soldeActuel = defaultAccount.getInitialBalance()
                .add(transactionRepository.sumByStatus(TransactionStatus.POINTEE, accountId));
        // Paired with soldeActuel in the hero card, so it's a "right now" figure too: always the
        // real current month, independent of whichever month is being browsed below.
        LocalDate currentMonthEnd = YearMonth.now().atEndOfMonth();
        BigDecimal previsionnelFinDeMois = soldeActuel
                .add(transactionRepository.sumByStatusUpToDate(TransactionStatus.PREVISIONNELLE, accountId, currentMonthEnd));
        BigDecimal soldeReelFinDeMois = defaultAccount.getInitialBalance()
                .add(transactionRepository.sumByStatusUpToDate(TransactionStatus.POINTEE, accountId, to));
        // The same projection, but for whichever month is being browsed - shown under "État du mois de ...".
        // Built from soldeReelFinDeMois (pointées up to `to` only), not soldeActuel: for a past month,
        // soldeActuel would wrongly pull in pointées that happened after that month too.
        BigDecimal previsionnelFinDeMoisSelectionne = soldeReelFinDeMois
                .add(transactionRepository.sumByStatusUpToDate(TransactionStatus.PREVISIONNELLE, accountId, to));

        return new DashboardResponse(
                month,
                revenus,
                depenses,
                soldeNet,
                soldeReelFinDeMois,
                soldeActuel,
                previsionnelFinDeMois,
                previsionnelFinDeMoisSelectionne,
                depensesParCategorie,
                budgetsParCategorie,
                previsionnellesNonPointees);
    }
}
