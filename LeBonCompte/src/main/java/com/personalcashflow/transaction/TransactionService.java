package com.personalcashflow.transaction;

import com.personalcashflow.account.Account;
import com.personalcashflow.account.AccountNotFoundException;
import com.personalcashflow.account.AccountRepository;
import com.personalcashflow.category.Category;
import com.personalcashflow.category.CategoryNotFoundException;
import com.personalcashflow.category.CategoryRepository;
import com.personalcashflow.transaction.dto.TransactionRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;

    public TransactionService(
            TransactionRepository transactionRepository,
            AccountRepository accountRepository,
            CategoryRepository categoryRepository) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public List<Transaction> search(
            String month, Long accountId, Long categoryId, TransactionStatus status, TransactionType type) {
        LocalDate[] range = monthRange(month);
        return transactionRepository.findAll(
                TransactionSpecifications.matching(range[0], range[1], accountId, categoryId, status, type));
    }

    /** Package-private for direct unit testing of the "YYYY-MM" to [firstDay, lastDay] parsing. */
    static LocalDate[] monthRange(String month) {
        if (month == null) {
            return new LocalDate[] {null, null};
        }
        YearMonth yearMonth = YearMonth.parse(month);
        return new LocalDate[] {yearMonth.atDay(1), yearMonth.atEndOfMonth()};
    }

    @Transactional(readOnly = true)
    public Transaction findById(Long id) {
        return transactionRepository.findById(id)
                .orElseThrow(() -> new TransactionNotFoundException(id));
    }

    public Transaction create(TransactionRequest request) {
        Account account = resolveAccount(request.accountId());
        Category category = resolveCategory(request.categoryId());
        Transaction transaction = new Transaction(
                request.date(),
                request.label(),
                signedAmount(request),
                account,
                category,
                request.type(),
                request.status());
        return transactionRepository.save(transaction);
    }

    public Transaction update(Long id, TransactionRequest request) {
        Transaction transaction = findById(id);
        Account account = resolveAccount(request.accountId());
        Category category = resolveCategory(request.categoryId());
        transaction.update(
                request.date(),
                request.label(),
                signedAmount(request),
                account,
                category,
                request.type(),
                request.status());
        return transaction;
    }

    public void delete(Long id) {
        Transaction transaction = findById(id);
        transactionRepository.delete(transaction);
    }

    private static BigDecimal signedAmount(TransactionRequest request) {
        return request.type() == TransactionType.DEPENSE
                ? request.amount().negate()
                : request.amount();
    }

    private Account resolveAccount(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
    }

    private Category resolveCategory(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CategoryNotFoundException(categoryId));
    }
}
