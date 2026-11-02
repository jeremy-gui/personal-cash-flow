package com.personalcashflow.analysis.dto;

import java.math.BigDecimal;

/**
 * {@code amount}/{@code partOfRevenue} cover the whole requested year. {@code currentMonthBudget}/
 * {@code currentMonthActual}/{@code currentMonthEcart} compare the latest elapsed month only
 * (null budget fields when the category has no budget for that month).
 */
public record CategoryYearSummaryResponse(
        Long categoryId,
        String categoryName,
        BigDecimal amount,
        BigDecimal partOfRevenue,
        BigDecimal currentMonthBudget,
        BigDecimal currentMonthActual,
        BigDecimal currentMonthEcart) {
}
