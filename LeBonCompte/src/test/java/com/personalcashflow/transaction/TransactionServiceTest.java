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
import com.personalcashflow.recurring.RecurringTransaction;
import com.personalcashflow.transaction.dto.TransactionRequest;
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
        Account account = new Account("Compte courant", AccountType.COURANT, BigDecimal.ZERO, null);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TransactionRequest request = new TransactionRequest(
                LocalDate.of(2026, 9, 5), "Carrefour", BigDecimal.valueOf(80), 1L, null,
                TransactionType.DEPENSE, null, false);

        Transaction created = transactionService.create(request);

        assertThat(created.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(-80));
    }

    @Test
    void create_shouldAlwaysStoreStatusPrevisionnelle() {
        Account account = new Account("Compte courant", AccountType.COURANT, BigDecimal.ZERO, null);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TransactionRequest request = new TransactionRequest(
                LocalDate.of(2026, 9, 5), "Carrefour", BigDecimal.valueOf(80), 1L, null,
                TransactionType.DEPENSE, null, false);

        Transaction created = transactionService.create(request);

        assertThat(created.getStatus()).isEqualTo(TransactionStatus.PREVISIONNELLE);
    }

    @Test
    void create_withRevenuType_shouldStorePositiveAmount() {
        Account account = new Account("Compte courant", AccountType.COURANT, BigDecimal.ZERO, null);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TransactionRequest request = new TransactionRequest(
                LocalDate.of(2026, 9, 8), "Salaire", BigDecimal.valueOf(2850), 1L, null,
                TransactionType.REVENU, null, false);

        Transaction created = transactionService.create(request);

        assertThat(created.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(2850));
    }

    @Test
    void create_whenAccountMissing_shouldThrowAccountNotFoundException() {
        when(accountRepository.findById(99L)).thenReturn(Optional.empty());

        TransactionRequest request = new TransactionRequest(
                LocalDate.now(), "Test", BigDecimal.TEN, 99L, null,
                TransactionType.DEPENSE, null, false);

        assertThatThrownBy(() -> transactionService.create(request))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void create_whenCategoryMissing_shouldThrowCategoryNotFoundException() {
        Account account = new Account("Compte courant", AccountType.COURANT, BigDecimal.ZERO, null);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        TransactionRequest request = new TransactionRequest(
                LocalDate.now(), "Test", BigDecimal.TEN, 1L, 99L,
                TransactionType.DEPENSE, null, false);

        assertThatThrownBy(() -> transactionService.create(request))
                .isInstanceOf(CategoryNotFoundException.class);
    }

    @Test
    void create_withTransfertType_shouldCreateMirrorOnTargetAccount() {
        Account source = accountWithId(1L, "Compte courant");
        Account target = accountWithId(2L, "LDDS");
        when(accountRepository.findById(1L)).thenReturn(Optional.of(source));
        when(accountRepository.findById(2L)).thenReturn(Optional.of(target));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TransactionRequest request = new TransactionRequest(
                LocalDate.of(2026, 9, 5), "Provision", BigDecimal.valueOf(150), 1L, null,
                TransactionType.TRANSFERT, 2L, true);

        Transaction created = transactionService.create(request);
        Transaction mirror = created.getLinkedTransaction();

        assertThat(created.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(-150));
        assertThat(mirror).isNotNull();
        assertThat(mirror.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(150));
        assertThat(mirror.getAccount()).isEqualTo(target);
        assertThat(mirror.getTransferAccount()).isEqualTo(source);
        assertThat(mirror.getSource()).isEqualTo(TransactionSource.TRANSFER);
        assertThat(mirror.getStatus()).isEqualTo(created.getStatus());
        assertThat(mirror.getLinkedTransaction()).isEqualTo(created);
    }

    @Test
    void update_shouldPropagateChangesToExistingMirror() {
        Account source = accountWithId(1L, "Compte courant");
        Account target = accountWithId(2L, "LDDS");
        when(accountRepository.findById(1L)).thenReturn(Optional.of(source));
        when(accountRepository.findById(2L)).thenReturn(Optional.of(target));
        Transaction transaction = Transaction.manual(
                LocalDate.of(2026, 9, 5), "Provision", BigDecimal.valueOf(-150), source, null,
                TransactionType.TRANSFERT, target);
        Transaction mirror = Transaction.mirrorOf(transaction);
        transaction.linkTo(mirror);
        mirror.linkTo(transaction);
        when(transactionRepository.findById(10L)).thenReturn(Optional.of(transaction));

        TransactionRequest request = new TransactionRequest(
                LocalDate.of(2026, 9, 10), "Provision ajustée", BigDecimal.valueOf(200), 1L, null,
                TransactionType.TRANSFERT, 2L, true);

        transactionService.update(10L, request);

        assertThat(mirror.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(200));
        assertThat(mirror.getLabel()).isEqualTo("Provision ajustée");
        assertThat(mirror.getDate()).isEqualTo(LocalDate.of(2026, 9, 10));
    }

    @Test
    void update_whenTransferAccountChanges_shouldRebuildMirrorOnNewAccount() {
        Account source = accountWithId(1L, "Compte courant");
        Account oldTarget = accountWithId(2L, "LDDS");
        Account newTarget = accountWithId(3L, "Livret A Louison");
        when(accountRepository.findById(1L)).thenReturn(Optional.of(source));
        when(accountRepository.findById(3L)).thenReturn(Optional.of(newTarget));
        Transaction transaction = Transaction.manual(
                LocalDate.of(2026, 9, 5), "Épargne", BigDecimal.valueOf(-50), source, null,
                TransactionType.TRANSFERT, oldTarget);
        Transaction oldMirror = Transaction.mirrorOf(transaction);
        transaction.linkTo(oldMirror);
        oldMirror.linkTo(transaction);
        when(transactionRepository.findById(10L)).thenReturn(Optional.of(transaction));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TransactionRequest request = new TransactionRequest(
                LocalDate.of(2026, 9, 5), "Épargne", BigDecimal.valueOf(50), 1L, null,
                TransactionType.TRANSFERT, 3L, true);

        transactionService.update(10L, request);

        verify(transactionRepository).delete(oldMirror);
        Transaction newMirror = transaction.getLinkedTransaction();
        assertThat(newMirror).isNotSameAs(oldMirror);
        assertThat(newMirror.getAccount()).isEqualTo(newTarget);
    }

    @Test
    void update_whenTypeChangesAwayFromTransfert_shouldDeleteMirror() {
        Account source = accountWithId(1L, "Compte courant");
        Account target = accountWithId(2L, "LDDS");
        when(accountRepository.findById(1L)).thenReturn(Optional.of(source));
        Transaction transaction = Transaction.manual(
                LocalDate.of(2026, 9, 5), "Provision", BigDecimal.valueOf(-150), source, null,
                TransactionType.TRANSFERT, target);
        Transaction mirror = Transaction.mirrorOf(transaction);
        transaction.linkTo(mirror);
        mirror.linkTo(transaction);
        when(transactionRepository.findById(10L)).thenReturn(Optional.of(transaction));

        TransactionRequest request = new TransactionRequest(
                LocalDate.of(2026, 9, 5), "Provision", BigDecimal.valueOf(150), 1L, null,
                TransactionType.DEPENSE, null, false);

        transactionService.update(10L, request);

        verify(transactionRepository).delete(mirror);
        assertThat(transaction.getLinkedTransaction()).isNull();
    }

    @Test
    void update_whenTypeChangesToTransfert_shouldCreateMirror() {
        Account source = accountWithId(1L, "Compte courant");
        Account target = accountWithId(2L, "LDDS");
        when(accountRepository.findById(1L)).thenReturn(Optional.of(source));
        when(accountRepository.findById(2L)).thenReturn(Optional.of(target));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Transaction transaction = Transaction.manual(
                LocalDate.of(2026, 9, 5), "Provision", BigDecimal.valueOf(-150), source, null,
                TransactionType.DEPENSE);
        when(transactionRepository.findById(10L)).thenReturn(Optional.of(transaction));

        TransactionRequest request = new TransactionRequest(
                LocalDate.of(2026, 9, 5), "Provision", BigDecimal.valueOf(150), 1L, null,
                TransactionType.TRANSFERT, 2L, true);

        transactionService.update(10L, request);

        assertThat(transaction.getLinkedTransaction()).isNotNull();
        assertThat(transaction.getLinkedTransaction().getAccount()).isEqualTo(target);
    }

    @Test
    void delete_whenTransactionHasMirror_shouldDeleteMirrorToo() {
        Account source = accountWithId(1L, "Compte courant");
        Account target = accountWithId(2L, "LDDS");
        Transaction transaction = Transaction.manual(
                LocalDate.of(2026, 9, 5), "Provision", BigDecimal.valueOf(-150), source, null,
                TransactionType.TRANSFERT, target);
        Transaction mirror = Transaction.mirrorOf(transaction);
        transaction.linkTo(mirror);
        mirror.linkTo(transaction);
        when(transactionRepository.findById(10L)).thenReturn(Optional.of(transaction));

        transactionService.delete(10L);

        verify(transactionRepository).delete(mirror);
        verify(transactionRepository).delete(transaction);
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
    void create_shouldLeaveOriginalDateAndLabelNull() {
        Account account = new Account("Compte courant", AccountType.COURANT, BigDecimal.ZERO, null);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TransactionRequest request = new TransactionRequest(
                LocalDate.of(2026, 9, 5), "Carrefour", BigDecimal.valueOf(80), 1L, null,
                TransactionType.DEPENSE, null, false);

        Transaction created = transactionService.create(request);

        assertThat(created.getOriginalDate()).isNull();
        assertThat(created.getOriginalLabel()).isNull();
    }

    @Test
    void update_shouldNotChangeOriginalDateAndLabel() {
        Account account = new Account("Compte courant", AccountType.COURANT, BigDecimal.ZERO, null);
        Transaction transaction = Transaction.imported(
                LocalDate.of(2026, 9, 5), "CARREFOUR", BigDecimal.TEN, account,
                TransactionType.DEPENSE, "hash", "raw");
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(transaction));
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));

        TransactionRequest request = new TransactionRequest(
                LocalDate.of(2026, 9, 6), "Courses", BigDecimal.valueOf(80), 1L, null,
                TransactionType.DEPENSE, null, false);

        Transaction updated = transactionService.update(1L, request);

        assertThat(updated.getLabel()).isEqualTo("Courses");
        assertThat(updated.getOriginalDate()).isEqualTo(LocalDate.of(2026, 9, 5));
        assertThat(updated.getOriginalLabel()).isEqualTo("CARREFOUR");
    }

    @Test
    void update_shouldMarkCategoryAndLabelAsConfirmed() {
        Account account = new Account("Compte courant", AccountType.COURANT, BigDecimal.ZERO, null);
        Transaction transaction = Transaction.imported(
                LocalDate.of(2026, 9, 5), "CARREFOUR", BigDecimal.TEN, account,
                TransactionType.DEPENSE, "hash", "raw");
        transaction.suggestCategory(new Category("Courses", null));
        transaction.suggestLabel("carrefour");
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(transaction));
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));

        TransactionRequest request = new TransactionRequest(
                LocalDate.of(2026, 9, 6), "Courses", BigDecimal.valueOf(80), 1L, null,
                TransactionType.DEPENSE, null, false);

        Transaction updated = transactionService.update(1L, request);

        assertThat(updated.isCategoryConfirmed()).isTrue();
        assertThat(updated.isLabelConfirmed()).isTrue();
    }

    @Test
    void confirmCategory_shouldMarkTheCategoryAsConfirmed() {
        Account account = new Account("Compte courant", AccountType.COURANT, BigDecimal.ZERO, null);
        Transaction transaction = Transaction.imported(
                LocalDate.of(2026, 9, 5), "CARREFOUR", BigDecimal.TEN, account,
                TransactionType.DEPENSE, "hash", "raw");
        transaction.suggestCategory(new Category("Courses", null));
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(transaction));

        var confirmed = transactionService.confirmCategory(1L);

        assertThat(confirmed.categoryConfirmed()).isTrue();
    }

    @Test
    void confirmCategory_whenTransactionMissing_shouldThrowTransactionNotFoundException() {
        when(transactionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.confirmCategory(99L))
                .isInstanceOf(TransactionNotFoundException.class);
    }

    @Test
    void confirmCategories_shouldConfirmEachGivenTransaction() {
        Account account = new Account("Compte courant", AccountType.COURANT, BigDecimal.ZERO, null);
        Transaction first = Transaction.imported(
                LocalDate.of(2026, 9, 5), "CARREFOUR", BigDecimal.TEN, account, TransactionType.DEPENSE, "h1", "r1");
        Transaction second = Transaction.imported(
                LocalDate.of(2026, 9, 6), "EDF", BigDecimal.TEN, account, TransactionType.DEPENSE, "h2", "r2");
        first.suggestCategory(new Category("Courses", null));
        second.suggestCategory(new Category("Logement", null));
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(first));
        when(transactionRepository.findById(2L)).thenReturn(Optional.of(second));

        var confirmed = transactionService.confirmCategories(java.util.List.of(1L, 2L));

        assertThat(confirmed).allMatch(TransactionResponse::categoryConfirmed);
    }

    @Test
    void confirmLabel_shouldMarkTheLabelAsConfirmed() {
        Account account = new Account("Compte courant", AccountType.COURANT, BigDecimal.ZERO, null);
        Transaction transaction = Transaction.imported(
                LocalDate.of(2026, 9, 5), "ACHAT CB CARREFOUR 05.09.26", BigDecimal.TEN, account,
                TransactionType.DEPENSE, "hash", "raw");
        transaction.suggestLabel("carrefour");
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(transaction));

        var confirmed = transactionService.confirmLabel(1L);

        assertThat(confirmed.labelConfirmed()).isTrue();
    }

    @Test
    void confirmLabel_whenTransactionMissing_shouldThrowTransactionNotFoundException() {
        when(transactionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.confirmLabel(99L))
                .isInstanceOf(TransactionNotFoundException.class);
    }

    @Test
    void confirmLabels_shouldConfirmEachGivenTransaction() {
        Account account = new Account("Compte courant", AccountType.COURANT, BigDecimal.ZERO, null);
        Transaction first = Transaction.imported(
                LocalDate.of(2026, 9, 5), "ACHAT CB CARREFOUR 05.09.26", BigDecimal.TEN, account, TransactionType.DEPENSE, "h1", "r1");
        Transaction second = Transaction.imported(
                LocalDate.of(2026, 9, 6), "PRELEVEMENT DE EDF", BigDecimal.TEN, account, TransactionType.DEPENSE, "h2", "r2");
        first.suggestLabel("carrefour");
        second.suggestLabel("EDF");
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(first));
        when(transactionRepository.findById(2L)).thenReturn(Optional.of(second));

        var confirmed = transactionService.confirmLabels(java.util.List.of(1L, 2L));

        assertThat(confirmed).allMatch(TransactionResponse::labelConfirmed);
    }

    @Test
    void delete_whenTransactionMissing_shouldThrowTransactionNotFoundException() {
        when(transactionRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.delete(42L))
                .isInstanceOf(TransactionNotFoundException.class);
    }

    @Test
    void delete_whenTransactionExists_shouldRemoveIt() {
        Account account = new Account("Compte courant", AccountType.COURANT, BigDecimal.ZERO, null);
        Transaction transaction = new Transaction(
                LocalDate.now(), "Test", BigDecimal.TEN, account, (Category) null,
                TransactionType.REVENU, TransactionStatus.POINTEE);
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(transaction));

        TransactionService.DeletedTransaction deleted = transactionService.delete(1L);

        verify(transactionRepository).delete(transaction);
        assertThat(deleted.recurringTransactionId()).isNull();
    }

    @Test
    void delete_whenTransactionIsRecurringGenerated_shouldReturnRecurringInfo() {
        Account account = new Account("Compte courant", AccountType.COURANT, BigDecimal.ZERO, null);
        Category category = new Category("Abonnements", null);
        RecurringTransaction recurring = new RecurringTransaction(
                "journal (paypal)", BigDecimal.valueOf(10.99), TransactionType.DEPENSE, category, LocalDate.of(2026, 9, 1));
        setId(recurring, 13L);
        Transaction transaction = Transaction.recurring(
                LocalDate.of(2026, 9, 1), "journal (paypal)", BigDecimal.valueOf(-10.99),
                account, category, TransactionType.DEPENSE, recurring);
        when(transactionRepository.findById(2L)).thenReturn(Optional.of(transaction));

        TransactionService.DeletedTransaction deleted = transactionService.delete(2L);

        assertThat(deleted.recurringTransactionId()).isEqualTo(13L);
        assertThat(deleted.date()).isEqualTo(LocalDate.of(2026, 9, 1));
    }

    @Test
    void categoryAndDescendantIds_shouldIncludeSelfAndAllDescendants() {
        Category child1 = new Category("Courses", null);
        Category child2 = new Category("Boulangerie", null);
        Category grandchild = new Category("Bio", null);
        setCategoryId(child1, 2L);
        setCategoryId(child2, 3L);
        setCategoryId(grandchild, 4L);
        when(categoryRepository.findByParentId(1L)).thenReturn(java.util.List.of(child1, child2));
        when(categoryRepository.findByParentId(2L)).thenReturn(java.util.List.of(grandchild));
        when(categoryRepository.findByParentId(3L)).thenReturn(java.util.List.of());
        when(categoryRepository.findByParentId(4L)).thenReturn(java.util.List.of());

        java.util.List<Long> ids = transactionService.categoryAndDescendantIds(1L);

        assertThat(ids).containsExactlyInAnyOrder(1L, 2L, 3L, 4L);
    }

    @Test
    void categoryAndDescendantIds_withLeafCategory_shouldReturnOnlyItself() {
        when(categoryRepository.findByParentId(5L)).thenReturn(java.util.List.of());

        java.util.List<Long> ids = transactionService.categoryAndDescendantIds(5L);

        assertThat(ids).containsExactly(5L);
    }

    private static Account accountWithId(Long id, String name) {
        Account account = new Account(name, AccountType.LIVRET, BigDecimal.ZERO, null);
        try {
            Field field = Account.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(account, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        return account;
    }

    private static void setCategoryId(Category category, Long id) {
        try {
            Field field = Category.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(category, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static void setId(RecurringTransaction recurring, Long id) {
        try {
            Field field = RecurringTransaction.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(recurring, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
