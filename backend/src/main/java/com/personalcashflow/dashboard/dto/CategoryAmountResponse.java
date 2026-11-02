package com.personalcashflow.dashboard.dto;

import java.math.BigDecimal;

/** {@code amount} is the positive spend magnitude for the category. */
public record CategoryAmountResponse(Long categoryId, String categoryName, BigDecimal amount) {
}
