package com.personalcashflow.reconciliation;

import com.personalcashflow.category.Category;
import com.personalcashflow.recurring.RecurringTransactionService;
import com.personalcashflow.transaction.Transaction;
import com.personalcashflow.transaction.TransactionNotFoundException;
import com.personalcashflow.transaction.TransactionRepository;
import com.personalcashflow.transaction.TransactionStatus;
import com.personalcashflow.transaction.dto.TransactionResponse;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A previsionnelle and its matching pointee describe a single real movement, so reconciling
 * them merges the rows into one rather than keeping both around: the pointee adopts the
 * previsionnelle's label (usually hand-written and more meaningful than the bank's wording) and
 * category (if any), then the previsionnelle is deleted. The pointee's original bank label is
 * not lost - it stays recoverable in its rawImportData. This is a one-way operation - there is
 * no "unmatch" that could resurrect the deleted row, and a pointee that already absorbed a
 * previsionnelle refuses any further match rather than silently overwriting its label/category.
 *
 * <p>A single bank movement sometimes covers several previsionnelles at once (e.g. one transfer
 * paying for two separate provisions) - {@link #match} also accepts several previsionnelle ids
 * against one pointee, concatenating their labels.
 */
@Service
@Transactional
public class ReconciliationService {

    private final TransactionRepository transactionRepository;
    private final RecurringTransactionService recurringTransactionService;

    public ReconciliationService(
            TransactionRepository transactionRepository, RecurringTransactionService recurringTransactionService) {
        this.transactionRepository = transactionRepository;
        this.recurringTransactionService = recurringTransactionService;
    }

    public TransactionResponse match(Long id, List<Long> matchWithIds) {
        if (matchWithIds.contains(id)) {
            throw new InvalidMatchException("A transaction cannot be matched with itself");
        }
        if (matchWithIds.size() == 1) {
            return matchPair(id, matchWithIds.get(0));
        }
        return matchGroup(id, matchWithIds);
    }

    /** The original 1:1 case - either id may be the previsionnelle or the pointee. */
    private TransactionResponse matchPair(Long id, Long matchWithId) {
        Transaction a = findById(id);
        Transaction b = findById(matchWithId);

        if (!a.getAccount().getId().equals(b.getAccount().getId())) {
            throw new InvalidMatchException("Transactions must belong to the same account");
        }
        if (!isOppositeStatusPair(a, b)) {
            throw new InvalidMatchException(
                    "One transaction must be PREVISIONNELLE and the other POINTEE");
        }

        Transaction previsionnelle = a.getStatus() == TransactionStatus.PREVISIONNELLE ? a : b;
        Transaction pointee = previsionnelle == a ? b : a;
        return reconcile(pointee, List.of(previsionnelle));
    }

    /** Several previsionnelles absorbed by a single pointee - id must be the pointee. */
    private TransactionResponse matchGroup(Long pointeeId, List<Long> previsionnelleIds) {
        Transaction pointee = findById(pointeeId);
        if (pointee.getStatus() != TransactionStatus.POINTEE) {
            throw new InvalidMatchException(
                    "Matching several transactions at once requires a single pointée as the target");
        }

        List<Transaction> previsionnelles = previsionnelleIds.stream().map(this::findById).toList();
        for (Transaction previsionnelle : previsionnelles) {
            if (previsionnelle.getStatus() != TransactionStatus.PREVISIONNELLE) {
                throw new InvalidMatchException("All matched transactions must be PREVISIONNELLE");
            }
            if (!previsionnelle.getAccount().getId().equals(pointee.getAccount().getId())) {
                throw new InvalidMatchException("Transactions must belong to the same account");
            }
        }
        if (distinctCategoryIds(previsionnelles).count() > 1) {
            throw new InvalidMatchException("The matched previsionnelles must all share the same category");
        }

        return reconcile(pointee, previsionnelles);
    }

    private TransactionResponse reconcile(Transaction pointee, List<Transaction> previsionnelles) {
        if (pointee.isMatched()) {
            throw new InvalidMatchException("This pointée has already absorbed a previsionnelle");
        }
        String label = previsionnelles.stream().map(Transaction::getLabel).collect(Collectors.joining(" + "));
        Category category = previsionnelles.get(0).getCategory();

        pointee.assignLabel(label);
        pointee.assignCategoryIfMissing(category);
        pointee.markMatched();
        for (Transaction previsionnelle : previsionnelles) {
            // A recurring instance absorbed by its bank line must not be regenerated for that month
            // (which would count it twice) - the same skip a manual delete records.
            if (previsionnelle.getRecurringTransaction() != null) {
                recurringTransactionService.recordSkip(
                        previsionnelle.getRecurringTransaction().getId(), previsionnelle.getDate());
            }
            transactionRepository.delete(previsionnelle);
        }
        return TransactionResponse.from(pointee);
    }

    private static Stream<Long> distinctCategoryIds(List<Transaction> previsionnelles) {
        return previsionnelles.stream()
                .map(t -> t.getCategory() != null ? t.getCategory().getId() : null)
                .distinct();
    }

    private static boolean isOppositeStatusPair(Transaction a, Transaction b) {
        return (a.getStatus() == TransactionStatus.PREVISIONNELLE && b.getStatus() == TransactionStatus.POINTEE)
                || (a.getStatus() == TransactionStatus.POINTEE && b.getStatus() == TransactionStatus.PREVISIONNELLE);
    }

    private Transaction findById(Long id) {
        return transactionRepository.findById(id)
                .orElseThrow(() -> new TransactionNotFoundException(id));
    }
}
