package com.personalcashflow.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.personalcashflow.account.AccountRepository;
import com.personalcashflow.budget.BudgetService;
import com.personalcashflow.dashboard.dto.DashboardResponse;
import com.personalcashflow.transaction.TransactionRepository;
import com.personalcashflow.transaction.TransactionService;
import com.personalcashflow.transaction.TransactionStatus;
import com.personalcashflow.transaction.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private TransactionService transactionService;

    @Mock
    private BudgetService budgetService;

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private DashboardService dashboardService;

    private static final LocalDate FROM = LocalDate.of(2026, 9, 1);
    private static final LocalDate TO = LocalDate.of(2026, 9, 30);

    private void stubCommon() {
        when(transactionRepository.sumByTypeAndDateRange(TransactionType.REVENU, FROM, TO))
                .thenReturn(BigDecimal.valueOf(2850));
        when(transactionRepository.sumByTypeAndDateRange(TransactionType.DEPENSE, FROM, TO))
                .thenReturn(BigDecimal.valueOf(-177.80));
        when(transactionRepository.sumDepenseGroupedByCategory(FROM, TO)).thenReturn(List.of());
        when(budgetService.findAllForMonth("2026-09")).thenReturn(List.of());
        when(transactionService.search("2026-09", null, null, TransactionStatus.PREVISIONNELLE, null))
                .thenReturn(List.of());
    }

    @Test
    void getDashboard_shouldComputeRevenusDepensesAndSoldeNet() {
        stubCommon();
        when(accountRepository.sumInitialBalance()).thenReturn(BigDecimal.ZERO);
        when(transactionRepository.sumByStatus(TransactionStatus.POINTEE)).thenReturn(BigDecimal.ZERO);
        when(transactionRepository.sumByStatusUpToDate(TransactionStatus.PREVISIONNELLE, TO))
                .thenReturn(BigDecimal.ZERO);

        DashboardResponse response = dashboardService.getDashboard("2026-09");

        assertThat(response.revenus()).isEqualByComparingTo(BigDecimal.valueOf(2850));
        assertThat(response.depenses()).isEqualByComparingTo(BigDecimal.valueOf(177.80));
        assertThat(response.soldeNet()).isEqualByComparingTo(BigDecimal.valueOf(2672.20));
    }

    @Test
    void getDashboard_shouldComputeSoldeActuelAndPrevisionnelFinDeMois() {
        stubCommon();
        when(accountRepository.sumInitialBalance()).thenReturn(BigDecimal.valueOf(1000));
        when(transactionRepository.sumByStatus(TransactionStatus.POINTEE)).thenReturn(BigDecimal.valueOf(-142.30));
        when(transactionRepository.sumByStatusUpToDate(TransactionStatus.PREVISIONNELLE, TO))
                .thenReturn(BigDecimal.valueOf(2000));

        DashboardResponse response = dashboardService.getDashboard("2026-09");

        assertThat(response.soldeActuel()).isEqualByComparingTo(BigDecimal.valueOf(857.70));
        assertThat(response.previsionnelFinDeMois()).isEqualByComparingTo(BigDecimal.valueOf(2857.70));
    }
}
