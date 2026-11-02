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
import java.time.YearMonth;
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

    private static final YearMonth MONTH = YearMonth.now();
    private static final LocalDate FROM = MONTH.atDay(1);
    private static final LocalDate TO = MONTH.atEndOfMonth();
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
        stubCommon(initialBalance, MONTH.toString(), FROM, TO);
    }

    private void stubCommon(BigDecimal initialBalance, String month, LocalDate from, LocalDate to) {
        when(accountRepository.findByIsDefaultTrue()).thenReturn(Optional.of(defaultAccount(initialBalance)));
        when(transactionRepository.sumByTypeAndDateRange(TransactionType.REVENU, ACCOUNT_ID, from, to))
                .thenReturn(BigDecimal.valueOf(2850));
        when(transactionRepository.sumByTypeAndDateRange(TransactionType.DEPENSE, ACCOUNT_ID, from, to))
                .thenReturn(BigDecimal.valueOf(-177.80));
        when(transactionRepository.sumTransfersByDirection(true, ACCOUNT_ID, from, to))
                .thenReturn(BigDecimal.valueOf(150));
        when(transactionRepository.sumTransfersByDirection(false, ACCOUNT_ID, from, to))
                .thenReturn(BigDecimal.valueOf(-500));
        when(transactionRepository.sumDepenseGroupedByCategory(ACCOUNT_ID, from, to)).thenReturn(List.of());
        when(budgetService.findAllForMonth(month)).thenReturn(List.of());
        when(transactionService.search(month, ACCOUNT_ID, null, TransactionStatus.PREVISIONNELLE, null))
                .thenReturn(List.of());
    }

    @Test
    void getDashboard_shouldComputeRevenusDepensesAndSoldeNet() {
        stubCommon(BigDecimal.ZERO);
        when(transactionRepository.sumByStatus(TransactionStatus.POINTEE, ACCOUNT_ID)).thenReturn(BigDecimal.ZERO);
        when(transactionRepository.sumByStatusUpToDate(TransactionStatus.PREVISIONNELLE, ACCOUNT_ID, TO))
                .thenReturn(BigDecimal.ZERO);
        when(transactionRepository.sumByStatusUpToDate(TransactionStatus.POINTEE, ACCOUNT_ID, TO))
                .thenReturn(BigDecimal.ZERO);

        DashboardResponse response = dashboardService.getDashboard(MONTH.toString());

        // Transfers count by direction: +150 incoming joins revenus, -500 outgoing joins dépenses.
        assertThat(response.revenus()).isEqualByComparingTo(BigDecimal.valueOf(3000));
        assertThat(response.depenses()).isEqualByComparingTo(BigDecimal.valueOf(677.80));
        assertThat(response.soldeNet()).isEqualByComparingTo(BigDecimal.valueOf(2322.20));
        // No category totals stubbed: the whole DEPENSE sum (not the transfers) is uncategorized.
        assertThat(response.depensesParCategorie()).singleElement().satisfies(c -> {
            assertThat(c.categoryId()).isNull();
            assertThat(c.categoryName()).isEqualTo("Non catégorisées");
            assertThat(c.amount()).isEqualByComparingTo(BigDecimal.valueOf(177.80));
        });
    }

    @Test
    void getDashboard_shouldComputeSoldeActuelPrevisionnelFinDeMoisAndSoldeReelFinDeMois() {
        // Requests the current month itself, so previsionnelFinDeMois is pinned to "now" via
        // soldeActuel while previsionnelFinDeMoisSelectionne is built from soldeReelFinDeMois -
        // stubbed to differ here on purpose to prove they're independent bases.
        stubCommon(BigDecimal.valueOf(1000));
        when(transactionRepository.sumByStatus(TransactionStatus.POINTEE, ACCOUNT_ID))
                .thenReturn(BigDecimal.valueOf(-142.30));
        when(transactionRepository.sumByStatusUpToDate(TransactionStatus.PREVISIONNELLE, ACCOUNT_ID, TO))
                .thenReturn(BigDecimal.valueOf(2000));
        when(transactionRepository.sumByStatusUpToDate(TransactionStatus.POINTEE, ACCOUNT_ID, TO))
                .thenReturn(BigDecimal.valueOf(-300));

        DashboardResponse response = dashboardService.getDashboard(MONTH.toString());

        assertThat(response.soldeActuel()).isEqualByComparingTo(BigDecimal.valueOf(857.70));
        assertThat(response.previsionnelFinDeMois()).isEqualByComparingTo(BigDecimal.valueOf(2857.70));
        assertThat(response.soldeReelFinDeMois()).isEqualByComparingTo(BigDecimal.valueOf(700));
        assertThat(response.previsionnelFinDeMoisSelectionne()).isEqualByComparingTo(BigDecimal.valueOf(2700));
    }

    @Test
    void getDashboard_previsionnelFinDeMoisSelectionne_forPastMonth_shouldIgnoreLaterPointeeTransactions() {
        YearMonth pastMonth = YearMonth.now().minusMonths(2);
        LocalDate pastFrom = pastMonth.atDay(1);
        LocalDate pastTo = pastMonth.atEndOfMonth();
        stubCommon(BigDecimal.valueOf(1000), pastMonth.toString(), pastFrom, pastTo);
        // soldeActuel pulls in everything pointée up to today, including months after pastMonth -
        // it must NOT leak into a past month's projection.
        when(transactionRepository.sumByStatus(TransactionStatus.POINTEE, ACCOUNT_ID))
                .thenReturn(BigDecimal.valueOf(5000));
        when(transactionRepository.sumByStatusUpToDate(
                        TransactionStatus.PREVISIONNELLE, ACCOUNT_ID, YearMonth.now().atEndOfMonth()))
                .thenReturn(BigDecimal.ZERO);
        when(transactionRepository.sumByStatusUpToDate(TransactionStatus.PREVISIONNELLE, ACCOUNT_ID, pastTo))
                .thenReturn(BigDecimal.valueOf(50));
        when(transactionRepository.sumByStatusUpToDate(TransactionStatus.POINTEE, ACCOUNT_ID, pastTo))
                .thenReturn(BigDecimal.valueOf(200));

        DashboardResponse response = dashboardService.getDashboard(pastMonth.toString());

        assertThat(response.soldeReelFinDeMois()).isEqualByComparingTo(BigDecimal.valueOf(1200));
        assertThat(response.previsionnelFinDeMoisSelectionne()).isEqualByComparingTo(BigDecimal.valueOf(1250));
    }

    @Test
    void getDashboard_previsionnelFinDeMois_staysOnCurrentMonthWhenBrowsingAnotherMonth() {
        YearMonth otherMonth = YearMonth.now().plusMonths(2);
        LocalDate otherFrom = otherMonth.atDay(1);
        LocalDate otherTo = otherMonth.atEndOfMonth();
        stubCommon(BigDecimal.valueOf(1000), otherMonth.toString(), otherFrom, otherTo);
        when(transactionRepository.sumByStatus(TransactionStatus.POINTEE, ACCOUNT_ID))
                .thenReturn(BigDecimal.ZERO);
        when(transactionRepository.sumByStatusUpToDate(
                        TransactionStatus.PREVISIONNELLE, ACCOUNT_ID, YearMonth.now().atEndOfMonth()))
                .thenReturn(BigDecimal.valueOf(100));
        when(transactionRepository.sumByStatusUpToDate(TransactionStatus.PREVISIONNELLE, ACCOUNT_ID, otherTo))
                .thenReturn(BigDecimal.valueOf(9000));
        when(transactionRepository.sumByStatusUpToDate(TransactionStatus.POINTEE, ACCOUNT_ID, otherTo))
                .thenReturn(BigDecimal.ZERO);

        DashboardResponse response = dashboardService.getDashboard(otherMonth.toString());

        assertThat(response.previsionnelFinDeMois()).isEqualByComparingTo(BigDecimal.valueOf(1100));
        assertThat(response.previsionnelFinDeMoisSelectionne()).isEqualByComparingTo(BigDecimal.valueOf(10000));
    }

    @Test
    void getDashboard_whenNoDefaultAccount_shouldThrowException() {
        when(accountRepository.findByIsDefaultTrue()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dashboardService.getDashboard(MONTH.toString()))
                .isInstanceOf(NoDefaultAccountException.class);
    }
}
