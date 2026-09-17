package com.personalcashflow.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.personalcashflow.account.Account;
import com.personalcashflow.account.AccountRepository;
import com.personalcashflow.account.AccountType;
import com.personalcashflow.budget.BudgetService;
import com.personalcashflow.dashboard.dto.DashboardResponse;
import com.personalcashflow.transaction.TransactionRepository;
import com.personalcashflow.transaction.TransactionService;
import com.personalcashflow.transaction.TransactionStatus;
import com.personalcashflow.transaction.TransactionType;
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
    private static final Long ACCOUNT_ID = 1L;

    private static Account defaultAccount(BigDecimal initialBalance) {
        Account account = new Account("Compte courant", AccountType.COURANT, initialBalance, null);
        try {
            Field idField = Account.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(account, ACCOUNT_ID);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        return account;
    }

    private void stubCommon(BigDecimal initialBalance) {
        when(accountRepository.findByIsDefaultTrue()).thenReturn(Optional.of(defaultAccount(initialBalance)));
        when(transactionRepository.sumByTypeAndDateRange(TransactionType.REVENU, ACCOUNT_ID, FROM, TO))
                .thenReturn(BigDecimal.valueOf(2850));
        when(transactionRepository.sumByTypeAndDateRange(TransactionType.DEPENSE, ACCOUNT_ID, FROM, TO))
                .thenReturn(BigDecimal.valueOf(-177.80));
        when(transactionRepository.sumDepenseGroupedByCategory(ACCOUNT_ID, FROM, TO)).thenReturn(List.of());
        when(budgetService.findAllForMonth("2026-09")).thenReturn(List.of());
        when(transactionService.search("2026-09", ACCOUNT_ID, null, TransactionStatus.PREVISIONNELLE, null))
                .thenReturn(List.of());
    }

    @Test
    void getDashboard_shouldComputeRevenusDepensesAndSoldeNet() {
        stubCommon(BigDecimal.ZERO);
        when(transactionRepository.sumByStatus(TransactionStatus.POINTEE, ACCOUNT_ID)).thenReturn(BigDecimal.ZERO);
        when(transactionRepository.sumByStatusUpToDate(TransactionStatus.PREVISIONNELLE, ACCOUNT_ID, TO))
                .thenReturn(BigDecimal.ZERO);

        DashboardResponse response = dashboardService.getDashboard("2026-09");

        assertThat(response.revenus()).isEqualByComparingTo(BigDecimal.valueOf(2850));
        assertThat(response.depenses()).isEqualByComparingTo(BigDecimal.valueOf(177.80));
        assertThat(response.soldeNet()).isEqualByComparingTo(BigDecimal.valueOf(2672.20));
    }

    @Test
    void getDashboard_shouldComputeSoldeActuelAndPrevisionnelFinDeMois() {
        stubCommon(BigDecimal.valueOf(1000));
        when(transactionRepository.sumByStatus(TransactionStatus.POINTEE, ACCOUNT_ID))
                .thenReturn(BigDecimal.valueOf(-142.30));
        when(transactionRepository.sumByStatusUpToDate(TransactionStatus.PREVISIONNELLE, ACCOUNT_ID, TO))
                .thenReturn(BigDecimal.valueOf(2000));

        DashboardResponse response = dashboardService.getDashboard("2026-09");

        assertThat(response.soldeActuel()).isEqualByComparingTo(BigDecimal.valueOf(857.70));
        assertThat(response.previsionnelFinDeMois()).isEqualByComparingTo(BigDecimal.valueOf(2857.70));
    }

    @Test
    void getDashboard_whenNoDefaultAccount_shouldThrowException() {
        when(accountRepository.findByIsDefaultTrue()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dashboardService.getDashboard("2026-09"))
                .isInstanceOf(NoDefaultAccountException.class);
    }
}
