package com.personalcashflow.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.personalcashflow.account.Account;
import com.personalcashflow.account.AccountNotFoundException;
import com.personalcashflow.account.AccountRepository;
import com.personalcashflow.account.AccountType;
import com.personalcashflow.category.Category;
import com.personalcashflow.category.CategoryNotFoundException;
import com.personalcashflow.category.CategoryRepository;
import com.personalcashflow.transaction.dto.TransactionRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private TransactionService transactionService;

    @Test
    void create_withDepenseType_shouldStoreNegativeAmount() {
        Account account = new Account("Compte courant", AccountType.COURANT, BigDecimal.ZERO);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TransactionRequest request = new TransactionRequest(
                LocalDate.of(2026, 9, 5), "Carrefour", BigDecimal.valueOf(80), 1L, null,
                TransactionType.DEPENSE, TransactionStatus.POINTEE);

        Transaction created = transactionService.create(request);

        assertThat(created.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(-80));
    }

    @Test
    void create_withRevenuType_shouldStorePositiveAmount() {
        Account account = new Account("Compte courant", AccountType.COURANT, BigDecimal.ZERO);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TransactionRequest request = new TransactionRequest(
                LocalDate.of(2026, 9, 8), "Salaire", BigDecimal.valueOf(2850), 1L, null,
                TransactionType.REVENU, TransactionStatus.PREVISIONNELLE);

        Transaction created = transactionService.create(request);

        assertThat(created.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(2850));
    }

    @Test
    void create_whenAccountMissing_shouldThrowAccountNotFoundException() {
        when(accountRepository.findById(99L)).thenReturn(Optional.empty());

        TransactionRequest request = new TransactionRequest(
                LocalDate.now(), "Test", BigDecimal.TEN, 99L, null,
                TransactionType.DEPENSE, TransactionStatus.POINTEE);

        assertThatThrownBy(() -> transactionService.create(request))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void create_whenCategoryMissing_shouldThrowCategoryNotFoundException() {
        Account account = new Account("Compte courant", AccountType.COURANT, BigDecimal.ZERO);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        TransactionRequest request = new TransactionRequest(
                LocalDate.now(), "Test", BigDecimal.TEN, 1L, 99L,
                TransactionType.DEPENSE, TransactionStatus.POINTEE);

        assertThatThrownBy(() -> transactionService.create(request))
                .isInstanceOf(CategoryNotFoundException.class);
    }

    @Test
    void monthRange_withMonth_shouldReturnFirstToLastDayOfMonth() {
        LocalDate[] range = TransactionService.monthRange("2026-09");

        assertThat(range).containsExactly(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
    }

    @Test
    void monthRange_withoutMonth_shouldReturnNoBounds() {
        LocalDate[] range = TransactionService.monthRange(null);

        assertThat(range).containsExactly(null, null);
    }

    @Test
    void delete_whenTransactionMissing_shouldThrowTransactionNotFoundException() {
        when(transactionRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.delete(42L))
                .isInstanceOf(TransactionNotFoundException.class);
    }

    @Test
    void delete_whenTransactionExists_shouldRemoveIt() {
        Account account = new Account("Compte courant", AccountType.COURANT, BigDecimal.ZERO);
        Transaction transaction = new Transaction(
                LocalDate.now(), "Test", BigDecimal.TEN, account, (Category) null,
                TransactionType.REVENU, TransactionStatus.POINTEE);
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(transaction));

        transactionService.delete(1L);

        verify(transactionRepository).delete(transaction);
    }
}
