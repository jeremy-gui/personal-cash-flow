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
import com.personalcashflow.transaction.TransactionService;
import com.personalcashflow.transaction.TransactionStatus;
import com.personalcashflow.transaction.TransactionType;
import com.personalcashflow.transaction.TypeNotAllowedException;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
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
    private RecurringTransactionSkipRepository skipRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private TransactionService transactionService;

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
    void ensureGeneratedForMonth_ofAnOutgoingTransfer_shouldGenerateARealTransferWithItsMirror() {
        Account ldds = accountWithId(7L);
        RecurringTransaction provision = recurringWithId(
                1L, "provision charges copro", BigDecimal.valueOf(150), TransactionType.TRANSFERT, LocalDate.of(2026, 10, 1));
        provision.changeTransfer(ldds, true);
        when(recurringTransactionRepository.findAll()).thenReturn(List.of(provision));
        when(accountRepository.findByIsDefaultTrue()).thenReturn(Optional.of(accountWithId(1L)));
        when(transactionRepository.existsByRecurringTransactionIdAndDate(eq(1L), any())).thenReturn(false);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        recurringTransactionService.ensureGeneratedForMonth("2026-10");

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        Transaction instance = captor.getValue();
        assertThat(instance.getAmount()).isEqualByComparingTo("-150");
        assertThat(instance.getTransferAccount()).isEqualTo(ldds);
        verify(transactionService).syncTransferMirror(instance);
    }

    @Test
    void create_ofATransferWithoutAnyCompteCible_shouldBeRefused() {
        when(categoryRepository.findById(9L)).thenReturn(Optional.of(categoryWithId(9L, "Épargne")));

        assertThatThrownBy(() -> recurringTransactionService.create(new RecurringTransactionRequest(
                        "épargne", BigDecimal.valueOf(100), TransactionType.TRANSFERT, 9L, false, null, null)))
                .isInstanceOf(MissingTransferAccountException.class);
    }

    @Test
    void ensureGeneratedForMonth_shouldTakeTheGenerationLockBeforeCheckingWhatExists() {
        when(recurringTransactionRepository.findAll()).thenReturn(List.of());

        recurringTransactionService.ensureGeneratedForMonth("2026-09");

        var order = org.mockito.Mockito.inOrder(recurringTransactionRepository);
        order.verify(recurringTransactionRepository).lockGeneration();
        order.verify(recurringTransactionRepository).findAll();
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
    void ensureGeneratedForMonth_shouldSkipMonthsExplicitlySkipped() {
        RecurringTransaction recurring = recurringWithId(
                1L, "journal (paypal)", BigDecimal.valueOf(10.99), TransactionType.DEPENSE, LocalDate.of(2026, 9, 1));
        Account account = accountWithId(2L);
        when(recurringTransactionRepository.findAll()).thenReturn(List.of(recurring));
        when(accountRepository.findByIsDefaultTrue()).thenReturn(Optional.of(account));
        when(transactionRepository.existsByRecurringTransactionIdAndDate(1L, LocalDate.of(2026, 9, 1))).thenReturn(false);
        when(skipRepository.existsByRecurringTransactionIdAndMonth(1L, LocalDate.of(2026, 9, 1))).thenReturn(true);

        recurringTransactionService.ensureGeneratedForMonth("2026-09");

        verify(transactionRepository, never()).save(any());
    }

    @Test
    void recordSkip_shouldSaveOnlyOnce() {
        when(skipRepository.existsByRecurringTransactionIdAndMonth(1L, LocalDate.of(2026, 9, 1)))
                .thenReturn(false, true);
        RecurringTransaction recurring = recurringWithId(
                1L, "journal (paypal)", BigDecimal.valueOf(10.99), TransactionType.DEPENSE, LocalDate.of(2026, 9, 1));
        when(recurringTransactionRepository.getReferenceById(1L)).thenReturn(recurring);

        recurringTransactionService.recordSkip(1L, LocalDate.of(2026, 9, 15));
        recurringTransactionService.recordSkip(1L, LocalDate.of(2026, 9, 20));

        verify(skipRepository, times(1)).save(any());
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
                new RecurringTransactionRequest("Loyer", BigDecimal.valueOf(1200), TransactionType.DEPENSE, 99L, false, null, null);

        assertThatThrownBy(() -> recurringTransactionService.create(request))
                .isInstanceOf(CategoryNotFoundException.class);
    }

    @Test
    void delete_whenMissing_shouldThrowNotFound() {
        when(recurringTransactionRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> recurringTransactionService.delete(42L))
                .isInstanceOf(RecurringTransactionNotFoundException.class);
    }

    @Test
    void updateAmount_shouldChangeDefinitionAndFutureUnpointedInstances() {
        RecurringTransaction recurring = recurringWithId(
                1L, "alimentation Alex", BigDecimal.valueOf(1600), TransactionType.REVENU, LocalDate.of(2026, 7, 1));
        Transaction instance = Transaction.recurring(LocalDate.of(2026, 10, 1), "alimentation Alex",
                BigDecimal.valueOf(1600), accountWithId(2L), null, TransactionType.REVENU, recurring);
        when(recurringTransactionRepository.findById(1L)).thenReturn(Optional.of(recurring));
        when(transactionRepository.findByRecurringTransactionIdAndStatusAndDateGreaterThanEqual(
                        eq(1L), eq(TransactionStatus.PREVISIONNELLE), eq(YearMonth.now().atDay(1))))
                .thenReturn(List.of(instance));

        var response = recurringTransactionService.updateAmount(1L, BigDecimal.valueOf(2420));

        assertThat(response.amount()).isEqualByComparingTo("2420");
        assertThat(instance.getAmount()).isEqualByComparingTo("2420");
    }

    @Test
    void update_shouldEditTheDefinitionAndCarryItToFutureUnpointedInstances() {
        RecurringTransaction recurring = recurringWithId(
                1L, "EDF", BigDecimal.valueOf(98.75), TransactionType.DEPENSE, LocalDate.of(2026, 7, 1));
        Category energie = categoryWithId(9L, "Énergie");
        Transaction instance = Transaction.recurring(LocalDate.of(2026, 10, 1), "EDF",
                BigDecimal.valueOf(-98.75), accountWithId(2L), null, TransactionType.DEPENSE, recurring);
        when(recurringTransactionRepository.findById(1L)).thenReturn(Optional.of(recurring));
        when(categoryRepository.findById(9L)).thenReturn(Optional.of(energie));
        when(transactionRepository.findByRecurringTransactionIdAndStatusAndDateGreaterThanEqual(
                        eq(1L), eq(TransactionStatus.PREVISIONNELLE), eq(YearMonth.now().atDay(1))))
                .thenReturn(List.of(instance));

        var response = recurringTransactionService.update(
                1L, new RecurringTransactionRequest("EDF (mensualité)", BigDecimal.valueOf(110), TransactionType.DEPENSE, 9L, true, null, null));

        assertThat(response.label()).isEqualTo("EDF (mensualité)");
        assertThat(response.categoryName()).isEqualTo("Énergie");
        assertThat(response.variableAmount()).isTrue();
        assertThat(instance.getLabel()).isEqualTo("EDF (mensualité)");
        assertThat(instance.getAmount()).isEqualByComparingTo("-110");
        assertThat(instance.getCategory()).isEqualTo(energie);
    }

    @Test
    void followRealAmount_ofAVariableRecurring_shouldTakeTheBankAmountForTheFollowingMonths() {
        RecurringTransaction edf = recurringWithId(
                1L, "EDF", BigDecimal.valueOf(98.75), TransactionType.DEPENSE, LocalDate.of(2026, 7, 1));
        edf.changeVariableAmount(true);
        Transaction november = Transaction.recurring(LocalDate.of(2026, 11, 1), "EDF",
                BigDecimal.valueOf(-98.75), accountWithId(2L), null, TransactionType.DEPENSE, edf);
        when(transactionRepository.findByRecurringTransactionIdAndStatusAndDateGreaterThanEqual(
                        1L, TransactionStatus.PREVISIONNELLE, LocalDate.of(2026, 11, 1)))
                .thenReturn(List.of(november));

        recurringTransactionService.followRealAmount(edf, BigDecimal.valueOf(-112.40), LocalDate.of(2026, 10, 1));

        assertThat(edf.getAmount()).isEqualByComparingTo("112.40");
        assertThat(november.getAmount()).isEqualByComparingTo("-112.40");
    }

    @Test
    void followRealAmount_ofAFixedRecurring_shouldChangeNothing() {
        RecurringTransaction pret = recurringWithId(
                1L, "prêt immo", BigDecimal.valueOf(1103.33), TransactionType.DEPENSE, LocalDate.of(2026, 7, 1));

        recurringTransactionService.followRealAmount(pret, BigDecimal.valueOf(-1100), LocalDate.of(2026, 10, 1));

        assertThat(pret.getAmount()).isEqualByComparingTo("1103.33");
        verify(transactionRepository, never()).findByRecurringTransactionIdAndStatusAndDateGreaterThanEqual(any(), any(), any());
    }

    @Test
    void create_withATypeTheCategoryDoesNotAllow_shouldBeRefused() {
        Category logement = categoryWithId(9L, "Logement");
        logement.changeAllowedTypes(List.of(TransactionType.DEPENSE));
        when(categoryRepository.findById(9L)).thenReturn(Optional.of(logement));

        assertThatThrownBy(() -> recurringTransactionService.create(
                        new RecurringTransactionRequest("loyer", BigDecimal.valueOf(800), TransactionType.REVENU, 9L, false, null, null)))
                .isInstanceOf(TypeNotAllowedException.class);
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
