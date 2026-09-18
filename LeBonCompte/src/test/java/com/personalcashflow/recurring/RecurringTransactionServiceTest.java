package com.personalcashflow.recurring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.personalcashflow.account.Account;
import com.personalcashflow.account.AccountRepository;
import com.personalcashflow.account.AccountType;
import com.personalcashflow.category.Category;
import com.personalcashflow.category.CategoryNotFoundException;
import com.personalcashflow.category.CategoryRepository;
import com.personalcashflow.dashboard.NoDefaultAccountException;
import com.personalcashflow.recurring.dto.RecurringTransactionRequest;
import com.personalcashflow.transaction.Transaction;
import com.personalcashflow.transaction.TransactionRepository;
import com.personalcashflow.transaction.TransactionType;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecurringTransactionServiceTest {

    @Mock
    private RecurringTransactionRepository recurringTransactionRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private RecurringTransactionService recurringTransactionService;

    @Test
    void ensureGeneratedForMonth_shouldBackfillEveryMonthFromStartThroughTarget() {
        RecurringTransaction recurring = recurringWithId(
                1L, "Loyer", BigDecimal.valueOf(1200), TransactionType.DEPENSE, LocalDate.of(2026, 7, 1));
        Account account = accountWithId(2L);
        when(recurringTransactionRepository.findAll()).thenReturn(List.of(recurring));
        when(accountRepository.findByIsDefaultTrue()).thenReturn(Optional.of(account));
        when(transactionRepository.existsByRecurringTransactionIdAndDate(eq(1L), any())).thenReturn(false);

        recurringTransactionService.ensureGeneratedForMonth("2026-09");

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, times(3)).save(captor.capture());
        assertThat(captor.getAllValues().stream().map(Transaction::getDate)).containsExactly(
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 1));
        assertThat(captor.getAllValues()).allSatisfy(t -> assertThat(t.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(-1200)));
    }

    @Test
    void ensureGeneratedForMonth_shouldSkipMonthsAlreadyGenerated() {
        RecurringTransaction recurring = recurringWithId(
                1L, "Loyer", BigDecimal.valueOf(1200), TransactionType.DEPENSE, LocalDate.of(2026, 7, 1));
        Account account = accountWithId(2L);
        when(recurringTransactionRepository.findAll()).thenReturn(List.of(recurring));
        when(accountRepository.findByIsDefaultTrue()).thenReturn(Optional.of(account));
        when(transactionRepository.existsByRecurringTransactionIdAndDate(1L, LocalDate.of(2026, 7, 1))).thenReturn(true);
        when(transactionRepository.existsByRecurringTransactionIdAndDate(1L, LocalDate.of(2026, 8, 1))).thenReturn(false);

        recurringTransactionService.ensureGeneratedForMonth("2026-08");

        verify(transactionRepository, times(1)).save(any());
    }

    @Test
    void ensureGeneratedForMonth_whenNoRecurringTransactions_shouldNotResolveAccount() {
        when(recurringTransactionRepository.findAll()).thenReturn(List.of());

        recurringTransactionService.ensureGeneratedForMonth("2026-09");

        verify(accountRepository, never()).findByIsDefaultTrue();
    }

    @Test
    void ensureGeneratedForMonth_whenNoDefaultAccount_shouldThrow() {
        RecurringTransaction recurring = recurringWithId(
                1L, "Loyer", BigDecimal.valueOf(1200), TransactionType.DEPENSE, LocalDate.of(2026, 9, 1));
        when(recurringTransactionRepository.findAll()).thenReturn(List.of(recurring));
        when(accountRepository.findByIsDefaultTrue()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> recurringTransactionService.ensureGeneratedForMonth("2026-09"))
                .isInstanceOf(NoDefaultAccountException.class);
    }

    @Test
    void create_whenCategoryMissing_shouldThrowNotFound() {
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        RecurringTransactionRequest request =
                new RecurringTransactionRequest("Loyer", BigDecimal.valueOf(1200), TransactionType.DEPENSE, 99L);

        assertThatThrownBy(() -> recurringTransactionService.create(request))
                .isInstanceOf(CategoryNotFoundException.class);
    }

    @Test
    void delete_whenMissing_shouldThrowNotFound() {
        when(recurringTransactionRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> recurringTransactionService.delete(42L))
                .isInstanceOf(RecurringTransactionNotFoundException.class);
    }

    private static RecurringTransaction recurringWithId(
            Long id, String label, BigDecimal amount, TransactionType type, LocalDate startMonth) {
        Category category = categoryWithId(1L, "Logement");
        RecurringTransaction recurring = new RecurringTransaction(label, amount, type, category, startMonth);
        setField(recurring, "id", id);
        return recurring;
    }

    private static Category categoryWithId(Long id, String name) {
        Category category = new Category(name, null);
        setField(category, "id", id);
        return category;
    }

    private static Account accountWithId(Long id) {
        Account account = new Account("Compte courant", AccountType.COURANT, BigDecimal.ZERO, null);
        setField(account, "id", id);
        return account;
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
