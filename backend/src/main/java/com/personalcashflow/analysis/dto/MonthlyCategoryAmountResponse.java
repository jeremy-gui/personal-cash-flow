package com.personalcashflow.analysis.dto;

import java.math.BigDecimal;

/** amount is the positive spend magnitude for the category, for one calendar month (1-12). */
public record MonthlyCategoryAmountResponse(int month, Long categoryId, String categoryName, BigDecimal amount) {
}
