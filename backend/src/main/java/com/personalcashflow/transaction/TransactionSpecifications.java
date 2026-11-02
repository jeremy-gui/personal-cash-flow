package com.personalcashflow.transaction;

import com.personalcashflow.account.Account;
import com.personalcashflow.category.Category;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/**
 * Postgres cannot infer a parameter's type when it only ever appears in an
 * {@code IS NULL} branch (the classic {@code (:param IS NULL OR field = :param)} pattern),
 * so optional filters are built as Criteria predicates instead of a single JPQL query.
 */
final class TransactionSpecifications {

    private TransactionSpecifications() {
    }

    static Specification<Transaction> matching(
            LocalDate from,
            LocalDate to,
            Long accountId,
            List<Long> categoryIds,
            TransactionStatus status,
            TransactionType type) {
        return (root, query, cb) -> {
            @SuppressWarnings("unchecked")
            Join<Transaction, Account> accountJoin = (Join<Transaction, Account>) (Join) root.fetch("account", JoinType.INNER);
            @SuppressWarnings("unchecked")
            Join<Transaction, Category> categoryJoin = (Join<Transaction, Category>) (Join) root.fetch("category", JoinType.LEFT);
            // transferAccountName is read in TransactionResponse.from() too, same reasoning as account/category.
            root.fetch("transferAccount", JoinType.LEFT);
            query.orderBy(cb.desc(root.get("date")), cb.desc(root.get("id")));

            List<Predicate> predicates = new ArrayList<>();
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("date"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("date"), to));
            }
            if (accountId != null) {
                predicates.add(cb.equal(accountJoin.get("id"), accountId));
            }
            if (categoryIds != null) {
                predicates.add(categoryJoin.get("id").in(categoryIds));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (type != null) {
                predicates.add(cb.equal(root.get("type"), type));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
