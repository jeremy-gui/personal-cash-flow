package com.personalcashflow.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.personalcashflow.account.Account;
import com.personalcashflow.account.AccountType;
import com.personalcashflow.category.Category;
import com.personalcashflow.transaction.Transaction;
import com.personalcashflow.transaction.TransactionNotFoundException;
import com.personalcashflow.transaction.TransactionRepository;
import com.personalcashflow.transaction.TransactionStatus;
import com.personalcashflow.transaction.TransactionType;
import com.personalcashflow.transaction.dto.TransactionResponse;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReconciliationServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private ReconciliationService reconciliationService;

    @Test
    void match_withSameTransaction_shouldThrowInvalidMatch() {
        assertThatThrownBy(() -> reconciliationService.match(1L, 1L))
                .isInstanceOf(InvalidMatchException.class);
    }

    @Test
    void match_whenTransactionMissing_shouldThrowNotFound() {
        when(transactionRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reconciliationService.match(1L, 2L))
                .isInstanceOf(TransactionNotFoundException.class);
    }

    @Test
    void match_withDifferentAccounts_shouldThrowInvalidMatch() {
        Account accountA = accountWithId(10L, "Compte A");
        Account accountB = accountWithId(20L, "Compte B");
        Transaction a = transaction(1L, accountA, null, TransactionStatus.PREVISIONNELLE);
        Transaction b = transaction(2L, accountB, null, TransactionStatus.POINTEE);
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(a));
        when(transactionRepository.findById(2L)).thenReturn(Optional.of(b));

        assertThatThrownBy(() -> reconciliationService.match(1L, 2L))
                .isInstanceOf(InvalidMatchException.class);
    }

    @Test
    void match_withSameStatusPair_shouldThrowInvalidMatch() {
        Account account = accountWithId(10L, "Compte courant");
        Transaction a = transaction(1L, account, null, TransactionStatus.POINTEE);
        Transaction b = transaction(2L, account, null, TransactionStatus.POINTEE);
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(a));
        when(transactionRepository.findById(2L)).thenReturn(Optional.of(b));

        assertThatThrownBy(() -> reconciliationService.match(1L, 2L))
                .isInstanceOf(InvalidMatchException.class);
    }

    @Test
    void match_withValidPair_shouldDeletePrevisionnelleAndKeepPointee() {
        Account account = accountWithId(10L, "Compte courant");
        Transaction previsionnelle = transaction(1L, account, null, TransactionStatus.PREVISIONNELLE);
        Transaction pointee = transaction(2L, account, null, TransactionStatus.POINTEE);
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(previsionnelle));
        when(transactionRepository.findById(2L)).thenReturn(Optional.of(pointee));

        TransactionResponse response = reconciliationService.match(1L, 2L);

        verify(transactionRepository).delete(previsionnelle);
        assertThat(response.id()).isEqualTo(pointee.getId());
        assertThat(response.status()).isEqualTo(TransactionStatus.POINTEE);
    }

    @Test
    void match_whenPointeeHasNoCategory_shouldCopyCategoryFromPrevisionnelle() {
        Account account = accountWithId(10L, "Compte courant");
        Category category = categoryWithId(5L, "Logement");
        Transaction previsionnelle = transaction(1L, account, category, TransactionStatus.PREVISIONNELLE);
        Transaction pointee = transaction(2L, account, null, TransactionStatus.POINTEE);
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(previsionnelle));
        when(transactionRepository.findById(2L)).thenReturn(Optional.of(pointee));

        reconciliationService.match(1L, 2L);

        assertThat(pointee.getCategory()).isEqualTo(category);
    }

    @Test
    void match_shouldAdoptPrevisionnelleLabelOnThePointee() {
        Account account = accountWithId(10L, "Compte courant");
        Transaction previsionnelle = transaction(1L, account, null, TransactionStatus.PREVISIONNELLE, "Loyer");
        Transaction pointee = Transaction.imported(
                LocalDate.now(),
                "PRLV LOYER",
                BigDecimal.TEN,
                account,
                TransactionType.DEPENSE,
                "hash",
                "05/09/2026;\"PRLV LOYER\";-10.00");
        setId(pointee, Transaction.class, 2L);
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(previsionnelle));
        when(transactionRepository.findById(2L)).thenReturn(Optional.of(pointee));

        reconciliationService.match(1L, 2L);

        assertThat(pointee.getLabel()).isEqualTo("Loyer");
        assertThat(pointee.getRawImportData()).contains("PRLV LOYER");
    }

    @Test
    void match_whenPointeeAlreadyHasCategory_shouldNotOverwriteIt() {
        Account account = accountWithId(10L, "Compte courant");
        Category previsionnelleCategory = categoryWithId(5L, "Logement");
        Category pointeeCategory = categoryWithId(6L, "Autre");
        Transaction previsionnelle = transaction(1L, account, previsionnelleCategory, TransactionStatus.PREVISIONNELLE);
        Transaction pointee = transaction(2L, account, pointeeCategory, TransactionStatus.POINTEE);
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(previsionnelle));
        when(transactionRepository.findById(2L)).thenReturn(Optional.of(pointee));

        reconciliationService.match(1L, 2L);

        assertThat(pointee.getCategory()).isEqualTo(pointeeCategory);
    }

    private static Transaction transaction(Long id, Account account, Category category, TransactionStatus status) {
        return transaction(id, account, category, status, "Test");
    }

    private static Transaction transaction(
            Long id, Account account, Category category, TransactionStatus status, String label) {
        Transaction transaction = new Transaction(
                LocalDate.now(), label, BigDecimal.TEN, account, category, TransactionType.DEPENSE, status);
        setId(transaction, Transaction.class, id);
        return transaction;
    }

    private static Account accountWithId(Long id, String name) {
        Account account = new Account(name, AccountType.COURANT, BigDecimal.ZERO);
        setId(account, Account.class, id);
        return account;
    }

    private static Category categoryWithId(Long id, String name) {
        Category category = new Category(name, null);
        setId(category, Category.class, id);
        return category;
    }

    private static void setId(Object entity, Class<?> entityClass, Long id) {
        try {
            Field idField = entityClass.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
