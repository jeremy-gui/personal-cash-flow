package com.personalcashflow.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

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

    @InjectMocks
    private DashboardService dashboardService;

    @Test
    void getDashboard_shouldComputeRevenusDepensesAndSoldeNet() {
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 30);
        when(transactionRepository.sumByTypeAndDateRange(TransactionType.REVENU, from, to))
                .thenReturn(BigDecimal.valueOf(2850));
        when(transactionRepository.sumByTypeAndDateRange(TransactionType.DEPENSE, from, to))
                .thenReturn(BigDecimal.valueOf(-177.80));
        when(transactionRepository.sumDepenseGroupedByCategory(from, to)).thenReturn(List.of());
        when(budgetService.findAllForMonth("2026-09")).thenReturn(List.of());
        when(transactionService.search("2026-09", null, null, TransactionStatus.PREVISIONNELLE, null))
                .thenReturn(List.of());

        DashboardResponse response = dashboardService.getDashboard("2026-09");

        assertThat(response.revenus()).isEqualByComparingTo(BigDecimal.valueOf(2850));
        assertThat(response.depenses()).isEqualByComparingTo(BigDecimal.valueOf(177.80));
        assertThat(response.soldeNet()).isEqualByComparingTo(BigDecimal.valueOf(2672.20));
    }
}
