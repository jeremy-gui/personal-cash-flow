package com.personalcashflow.recurring;

import com.personalcashflow.account.Account;
import com.personalcashflow.account.AccountRepository;
import com.personalcashflow.category.Category;
import com.personalcashflow.category.CategoryNotFoundException;
import com.personalcashflow.category.CategoryRepository;
import com.personalcashflow.dashboard.NoDefaultAccountException;
import com.personalcashflow.recurring.dto.RecurringTransactionRequest;
import com.personalcashflow.recurring.dto.RecurringTransactionResponse;
import com.personalcashflow.transaction.Transaction;
import com.personalcashflow.transaction.TransactionRepository;
import com.personalcashflow.transaction.TransactionType;
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
    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final AccountRepository accountRepository;

    public RecurringTransactionService(
            RecurringTransactionRepository recurringTransactionRepository,
            TransactionRepository transactionRepository,
            CategoryRepository categoryRepository,
            AccountRepository accountRepository) {
        this.recurringTransactionRepository = recurringTransactionRepository;
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
        this.accountRepository = accountRepository;
    }

    @Transactional(readOnly = true)
    public List<RecurringTransactionResponse> findAll() {
        return recurringTransactionRepository.findAll().stream()
                .map(RecurringTransactionResponse::of)
                .toList();
    }

    public RecurringTransactionResponse create(RecurringTransactionRequest request) {
        Category category = resolveCategory(request.categoryId());
        RecurringTransaction recurring = new RecurringTransaction(
                request.label(), request.amount(), request.type(), category, YearMonth.now().atDay(1));
        recurringTransactionRepository.save(recurring);
        ensureGeneratedForMonth(YearMonth.now().toString());
        return RecurringTransactionResponse.of(recurring);
    }

    public void delete(Long id) {
        RecurringTransaction recurring = findById(id);
        recurringTransactionRepository.delete(recurring);
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
                if (!transactionRepository.existsByRecurringTransactionIdAndDate(recurring.getId(), date)) {
                    transactionRepository.save(Transaction.recurring(
                            date,
                            recurring.getLabel(),
                            signedAmount(recurring),
                            account,
                            recurring.getCategory(),
                            recurring.getType(),
                            recurring));
                }
                cursor = cursor.plusMonths(1);
            }
        }
    }

    private static BigDecimal signedAmount(RecurringTransaction recurring) {
        return recurring.getType() == TransactionType.DEPENSE
                ? recurring.getAmount().negate()
                : recurring.getAmount();
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
