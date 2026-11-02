package com.personalcashflow.imports.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ReconciliationSummaryResponse(
        int totalOfxRows,
        int matched,
        int alreadyReconciled,
        int ambiguousUnresolved,
        int unmatched,
        List<UnresolvedRow> ambiguousDetails,
        List<UnresolvedRow> unmatchedDetails) {

    public record UnresolvedRow(LocalDate date, String label, BigDecimal amount) {
    }
}
