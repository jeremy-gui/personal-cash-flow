package com.personalcashflow.transaction.dto;

import com.personalcashflow.transaction.Transaction;
import com.personalcashflow.transaction.TransactionSource;
import com.personalcashflow.transaction.TransactionStatus;
import com.personalcashflow.transaction.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionResponse(
        Long id,
        LocalDate date,
        String label,
        BigDecimal amount,
        Long accountId,
        String accountName,
        Long categoryId,
        String categoryName,
        TransactionType type,
        TransactionStatus status,
        TransactionSource source,
        LocalDate originalDate,
        String originalLabel,
        boolean matched,
        boolean categoryConfirmed,
        boolean labelConfirmed) {

    public static TransactionResponse from(Transaction transaction) {
        var category = transaction.getCategory();
        return new TransactionResponse(
                transaction.getId(),
                transaction.getDate(),
                transaction.getLabel(),
                transaction.getAmount(),
                transaction.getAccount().getId(),
                transaction.getAccount().getName(),
                category != null ? category.getId() : null,
                category != null ? category.getName() : null,
                transaction.getType(),
                transaction.getStatus(),
                transaction.getSource(),
                transaction.getOriginalDate(),
                transaction.getOriginalLabel(),
                transaction.isMatched(),
                transaction.isCategoryConfirmed(),
                transaction.isLabelConfirmed());
    }
}
