package com.personalcashflow.transaction;

import com.personalcashflow.account.Account;
import com.personalcashflow.account.AccountNotFoundException;
import com.personalcashflow.account.AccountRepository;
import com.personalcashflow.category.Category;
import com.personalcashflow.category.CategoryNotFoundException;
import com.personalcashflow.category.CategoryRepository;
import com.personalcashflow.transaction.dto.TransactionRequest;
import com.personalcashflow.transaction.dto.TransactionResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
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
        List<Long> categoryIds = categoryId != null ? categoryAndDescendantIds(categoryId) : null;
        return transactionRepository.findAll(
                TransactionSpecifications.matching(range[0], range[1], accountId, categoryIds, status, type));
    }

    /** Selecting a parent category should also surface transactions filed under any of its descendants. */
    List<Long> categoryAndDescendantIds(Long categoryId) {
        List<Long> ids = new ArrayList<>();
        Deque<Long> toVisit = new ArrayDeque<>(List.of(categoryId));
        while (!toVisit.isEmpty()) {
            Long current = toVisit.poll();
            ids.add(current);
            categoryRepository.findByParentId(current).forEach(child -> toVisit.add(child.getId()));
        }
        return ids;
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
        TypeNotAllowedException.check(request.type(), category);
        Account transferAccount = resolveTransferAccount(request);
        Transaction transaction = Transaction.manual(
                request.date(),
                request.label(),
                signedAmount(request),
                account,
                category,
                request.type(),
                transferAccount);
        transactionRepository.save(transaction);
        syncTransferMirror(transaction);
        return transaction;
    }

    public Transaction update(Long id, TransactionRequest request) {
        Transaction transaction = findById(id);
        Account account = resolveAccount(request.accountId());
        Category category = resolveCategory(request.categoryId());
        // A pointée is exempt: its type comes from the real bank amount's sign, and it must stay
        // categorizable anywhere.
        if (transaction.getStatus() != TransactionStatus.POINTEE) {
            TypeNotAllowedException.check(request.type(), category);
        }
        Account transferAccount = resolveTransferAccount(request);
        transaction.update(
                request.date(),
                request.label(),
                signedAmount(request),
                account,
                category,
                request.type(),
                transferAccount);
        syncTransferMirror(transaction);
        return transaction;
    }


    /**
     * Creates, updates or removes the mirror row on {@link Transaction#getTransferAccount()} so a
     * TRANSFERT always has both legs recorded. Not a transfer (any more) -> any existing mirror is
     * dropped. Already has one -> keep it in sync (also handles the target account changing: the
     * old mirror belongs to the wrong account, so it's dropped and rebuilt against the new one).
     */
    public void syncTransferMirror(Transaction transaction) {
        if (transaction.getType() != TransactionType.TRANSFERT || transaction.getTransferAccount() == null) {
            removeMirrorIfPresent(transaction);
            return;
        }
        Transaction mirror = transaction.getLinkedTransaction();
        if (mirror != null && mirror.getAccount().getId().equals(transaction.getTransferAccount().getId())) {
            mirror.mirrorFrom(transaction);
            return;
        }
        removeMirrorIfPresent(transaction);
        Transaction newMirror = Transaction.mirrorOf(transaction);
        transactionRepository.save(newMirror);
        transaction.linkTo(newMirror);
        newMirror.linkTo(transaction);
    }

    private void removeMirrorIfPresent(Transaction transaction) {
        Transaction mirror = transaction.getLinkedTransaction();
        if (mirror != null) {
            transaction.unlink();
            transactionRepository.delete(mirror);
        }
    }

    private Account resolveTransferAccount(TransactionRequest request) {
        if (request.type() != TransactionType.TRANSFERT || request.transferAccountId() == null) {
            return null;
        }
        return resolveAccount(request.transferAccountId());
    }

    /** Recurring-generated instance info, if any, so a caller can tell the recurrence to skip regenerating it. */
    public record DeletedTransaction(Long recurringTransactionId, LocalDate date) {
    }

    public DeletedTransaction delete(Long id) {
        Transaction transaction = findById(id);
        Long recurringTransactionId = transaction.getRecurringTransaction() != null
                ? transaction.getRecurringTransaction().getId()
                : null;
        LocalDate date = transaction.getDate();
        removeMirrorIfPresent(transaction);
        transactionRepository.delete(transaction);
        return new DeletedTransaction(recurringTransactionId, date);
    }

    /**
     * Builds the response here, still inside the transaction: {@code open-in-view} is disabled,
     * so returning the bare entity and mapping it to a response in the controller (as most other
     * endpoints do) would try to lazily load its account association after the session has
     * already closed. {@link com.personalcashflow.reconciliation.ReconciliationService} follows
     * the same rule for the same reason.
     */
    public TransactionResponse confirmCategory(Long id) {
        Transaction transaction = findById(id);
        transaction.confirmCategory();
        return TransactionResponse.from(transaction);
    }

    public List<TransactionResponse> confirmCategories(List<Long> ids) {
        return ids.stream().map(this::confirmCategory).toList();
    }

    public TransactionResponse confirmLabel(Long id) {
        Transaction transaction = findById(id);
        transaction.confirmLabel();
        return TransactionResponse.from(transaction);
    }

    public List<TransactionResponse> confirmLabels(List<Long> ids) {
        return ids.stream().map(this::confirmLabel).toList();
    }

    /** DEPENSE is always negative and REVENU always positive; TRANSFERT has no inherent direction
     *  so {@code outgoing} decides (true = leaves this account, like a DEPENSE). */
    private static BigDecimal signedAmount(TransactionRequest request) {
        boolean negative = request.type() == TransactionType.DEPENSE
                || (request.type() == TransactionType.TRANSFERT && request.outgoing());
        return negative ? request.amount().negate() : request.amount();
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
