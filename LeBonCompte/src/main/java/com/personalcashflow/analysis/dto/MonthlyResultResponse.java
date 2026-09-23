package com.personalcashflow.analysis.dto;

import java.math.BigDecimal;

/** revenus/depenses/epargne are always positive magnitudes; resultat can be negative. TRANSFERT
 *  (including provisioning) never counts as either revenu or depense. */
public record MonthlyResultResponse(
        int month, BigDecimal revenus, BigDecimal depenses, BigDecimal resultat, BigDecimal epargne) {
}
