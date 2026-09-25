package com.personalcashflow.recurring.dto;

import com.personalcashflow.recurring.RecurringTransaction;
import com.personalcashflow.transaction.TransactionType;
import java.math.BigDecimal;

public record RecurringTransactionResponse(
        Long id,
        String label,
        BigDecimal amount,
        TransactionType type,
        Long categoryId,
        String categoryName,
        String startMonth,
        boolean variableAmount) {

    public static RecurringTransactionResponse of(RecurringTransaction recurring) {
        return new RecurringTransactionResponse(
                recurring.getId(),
                recurring.getLabel(),
                recurring.getAmount(),
                recurring.getType(),
                recurring.getCategory().getId(),
                recurring.getCategory().getName(),
                recurring.getStartMonth().toString().substring(0, 7),
                recurring.isVariableAmount());
    }
}
