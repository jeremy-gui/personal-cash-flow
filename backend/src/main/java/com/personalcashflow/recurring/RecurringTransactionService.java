package com.personalcashflow.recurring;

import com.personalcashflow.account.Account;
import com.personalcashflow.account.AccountNotFoundException;
import com.personalcashflow.account.AccountRepository;
import com.personalcashflow.category.Category;
import com.personalcashflow.category.CategoryNotFoundException;
import com.personalcashflow.category.CategoryRepository;
import com.personalcashflow.dashboard.NoDefaultAccountException;
import com.personalcashflow.recurring.dto.RecurringTransactionRequest;
import com.personalcashflow.recurring.dto.RecurringTransactionResponse;
import com.personalcashflow.transaction.Transaction;
import com.personalcashflow.transaction.TransactionRepository;
import com.personalcashflow.transaction.TransactionService;
import com.personalcashflow.transaction.TransactionStatus;
import com.personalcashflow.transaction.TransactionType;
import com.personalcashflow.transaction.TypeNotAllowedException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RecurringTransactionService {

    private final RecurringTransactionRepository recurringTransactionRepository;
    private final RecurringTransactionSkipRepository skipRepository;
    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final AccountRepository accountRepository;
    private final TransactionService transactionService;

    public RecurringTransactionService(
            RecurringTransactionRepository recurringTransactionRepository,
            RecurringTransactionSkipRepository skipRepository,
            TransactionRepository transactionRepository,
            CategoryRepository categoryRepository,
            AccountRepository accountRepository,
            TransactionService transactionService) {
        this.recurringTransactionRepository = recurringTransactionRepository;
        this.skipRepository = skipRepository;
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
        this.accountRepository = accountRepository;
        this.transactionService = transactionService;
    }

    @Transactional(readOnly = true)
    public List<RecurringTransactionResponse> findAll() {
        return recurringTransactionRepository.findAll().stream()
                .map(RecurringTransactionResponse::of)
                .toList();
    }

    public RecurringTransactionResponse create(RecurringTransactionRequest request) {
        Category category = resolveCategory(request.categoryId());
        TypeNotAllowedException.check(request.type(), category);
        RecurringTransaction recurring = new RecurringTransaction(
                request.label(), request.amount(), request.type(), category, YearMonth.now().atDay(1));
        recurring.changeVariableAmount(request.variableAmount());
        applyTransfer(recurring, request, category);
        recurringTransactionRepository.save(recurring);
        ensureGeneratedForMonth(YearMonth.now().toString());
        return RecurringTransactionResponse.of(recurring);
    }

    /**
     * Changes the recurring amount from the current month on: the definition itself, plus every
     * instance already generated from this month that is still a prévisionnelle. Pointées (and past
     * months) keep their real amount.
     */
    public RecurringTransactionResponse updateAmount(Long id, BigDecimal amount) {
        RecurringTransaction recurring = findById(id);
        recurring.changeAmount(amount);
        BigDecimal signed = signedAmount(recurring);
        transactionRepository
                .findByRecurringTransactionIdAndStatusAndDateGreaterThanEqual(
                        id, TransactionStatus.PREVISIONNELLE, YearMonth.now().atDay(1))
                .forEach(instance -> {
                    instance.changeAmount(signed);
                    transactionService.syncTransferMirror(instance);
                });
        return RecurringTransactionResponse.of(recurring);
    }

    /**
     * A variable-amount recurring follows the last real amount: once one of its instances was
     * matched to a bank line paying {@code realAmount}, that becomes its amount, carried over to the
     * prévisionnelles of the following months. No-op for a fixed-amount recurring.
     */
    // ponytail: last match wins - reconciling an older month after a newer one reapplies the older
    // amount; track the date of the last applied amount if that ever matters.
    public void followRealAmount(RecurringTransaction recurring, BigDecimal realAmount, LocalDate matchedDate) {
        if (!recurring.isVariableAmount()) {
            return;
        }
        recurring.changeAmount(realAmount.abs());
        BigDecimal signed = signedAmount(recurring);
        transactionRepository
                .findByRecurringTransactionIdAndStatusAndDateGreaterThanEqual(
                        recurring.getId(), TransactionStatus.PREVISIONNELLE, YearMonth.from(matchedDate).plusMonths(1).atDay(1))
                .forEach(instance -> {
                    instance.changeAmount(signed);
                    transactionService.syncTransferMirror(instance);
                });
    }

    /**
     * Edits the whole definition (libellé, montant, type, catégorie) and, like {@link #updateAmount},
     * carries it over to every instance already generated from the current month on that is still
     * a prévisionnelle. Pointées and past months are left as they are.
     */
    public RecurringTransactionResponse update(Long id, RecurringTransactionRequest request) {
        RecurringTransaction recurring = findById(id);
        Category category = resolveCategory(request.categoryId());
        TypeNotAllowedException.check(request.type(), category);
        recurring.update(request.label(), request.amount(), request.type(), category);
        recurring.changeVariableAmount(request.variableAmount());
        applyTransfer(recurring, request, category);
        BigDecimal signed = signedAmount(recurring);
        transactionRepository
                .findByRecurringTransactionIdAndStatusAndDateGreaterThanEqual(
                        id, TransactionStatus.PREVISIONNELLE, YearMonth.now().atDay(1))
                .forEach(instance -> {
                    instance.followRecurring(
                            recurring.getLabel(), signed, category, recurring.getType(), recurring.getTransferAccount());
                    transactionService.syncTransferMirror(instance);
                });
        return RecurringTransactionResponse.of(recurring);
    }

    public void delete(Long id) {
        RecurringTransaction recurring = findById(id);
        recurringTransactionRepository.delete(recurring);
    }

    /**
     * Remembers that this recurring definition's instance for this month was explicitly deleted,
     * so {@link #ensureGeneratedForMonth} never silently recreates it. No-op if already recorded.
     */
    public void recordSkip(Long recurringTransactionId, LocalDate date) {
        LocalDate monthStart = YearMonth.from(date).atDay(1);
        if (skipRepository.existsByRecurringTransactionIdAndMonth(recurringTransactionId, monthStart)) {
            return;
        }
        skipRepository.save(new RecurringTransactionSkip(
                recurringTransactionRepository.getReferenceById(recurringTransactionId), monthStart));
    }

    /**
     * Materializes every recurring definition as a PREVISIONNELLE transaction for each month from
     * its start through {@code month}, skipping months already generated. Called before any read
     * that depends on a month's transactions (Transactions list, Dashboard, Budgets), so viewing a
     * month is what "activates" its recurring instances - no scheduler needed. Backfills every
     * skipped month in between, not just the requested one, since running-balance figures depend on
     * everything up to that month.
     */
    public void ensureGeneratedForMonth(String month) {
        // Check-then-insert below: serialize concurrent calls or each would insert its own copy.
        recurringTransactionRepository.lockGeneration();
        List<RecurringTransaction> recurrences = recurringTransactionRepository.findAll();
        if (recurrences.isEmpty()) {
            return;
        }
        YearMonth target = YearMonth.parse(month);
        Account account = accountRepository.findByIsDefaultTrue().orElseThrow(NoDefaultAccountException::new);
        for (RecurringTransaction recurring : recurrences) {
            YearMonth cursor = YearMonth.from(recurring.getStartMonth());
            while (!cursor.isAfter(target)) {
                LocalDate date = cursor.atDay(1);
                boolean alreadyGenerated = transactionRepository.existsByRecurringTransactionIdAndDate(recurring.getId(), date);
                boolean skipped = skipRepository.existsByRecurringTransactionIdAndMonth(recurring.getId(), date);
                if (!alreadyGenerated && !skipped) {
                    Transaction instance = transactionRepository.save(Transaction.recurring(
                            date,
                            recurring.getLabel(),
                            signedAmount(recurring),
                            account,
                            recurring.getCategory(),
                            recurring.getType(),
                            recurring,
                            recurring.getTransferAccount()));
                    // A TRANSFERT instance gets its other leg on the target account, like a hand-entered one.
                    transactionService.syncTransferMirror(instance);
                }
                cursor = cursor.plusMonths(1);
            }
        }
    }

    /** DEPENSE and outgoing TRANSFERT leave the account (negative); REVENU and incoming TRANSFERT arrive. */
    private static BigDecimal signedAmount(RecurringTransaction recurring) {
        boolean negative = recurring.getType() == TransactionType.DEPENSE
                || (recurring.getType() == TransactionType.TRANSFERT && recurring.isOutgoing());
        return negative ? recurring.getAmount().negate() : recurring.getAmount();
    }

    /** A TRANSFERT needs a target: the requested one, else the category's compte cible. */
    private void applyTransfer(RecurringTransaction recurring, RecurringTransactionRequest request, Category category) {
        Account target = null;
        if (recurring.getType() == TransactionType.TRANSFERT) {
            target = request.transferAccountId() != null
                    ? accountRepository.findById(request.transferAccountId())
                            .orElseThrow(() -> new AccountNotFoundException(request.transferAccountId()))
                    : category.getDefaultTransferAccount();
            if (target == null) {
                throw new MissingTransferAccountException();
            }
        }
        recurring.changeTransfer(target, request.outgoing() == null || request.outgoing());
    }

    private RecurringTransaction findById(Long id) {
        return recurringTransactionRepository.findById(id)
                .orElseThrow(() -> new RecurringTransactionNotFoundException(id));
    }

    private Category resolveCategory(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CategoryNotFoundException(categoryId));
    }
}
