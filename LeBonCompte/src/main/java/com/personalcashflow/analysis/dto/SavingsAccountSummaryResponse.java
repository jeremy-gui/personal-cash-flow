package com.personalcashflow.analysis.dto;

import java.math.BigDecimal;

public record SavingsAccountSummaryResponse(Long accountId, String accountName, BigDecimal contributed) {
}
