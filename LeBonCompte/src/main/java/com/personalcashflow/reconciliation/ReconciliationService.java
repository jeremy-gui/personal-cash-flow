package com.personalcashflow.reconciliation;

import com.personalcashflow.transaction.Transaction;
import com.personalcashflow.transaction.TransactionNotFoundException;
import com.personalcashflow.transaction.TransactionRepository;
import com.personalcashflow.transaction.TransactionStatus;
import com.personalcashflow.transaction.dto.TransactionResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A previsionnelle and its matching pointee describe a single real movement, so reconciling
 * them merges the two rows into one rather than keeping both around: the previsionnelle's
 * category (if any) is copied onto the pointee, then the previsionnelle is deleted. This is a
 * one-way operation - there is no "unmatch" that could resurrect the deleted row.
 */
@Service
@Transactional
public class ReconciliationService {

    private final TransactionRepository transactionRepository;

    public ReconciliationService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public TransactionResponse match(Long id, Long matchWithId) {
        if (id.equals(matchWithId)) {
            throw new InvalidMatchException("A transaction cannot be matched with itself");
        }

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

        pointee.assignCategoryIfMissing(previsionnelle.getCategory());
        transactionRepository.delete(previsionnelle);
        return TransactionResponse.from(pointee);
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
