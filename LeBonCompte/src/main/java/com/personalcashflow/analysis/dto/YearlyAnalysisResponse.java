package com.personalcashflow.analysis.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Household-wide: every sum spans all accounts, not just the default one (a TRANSFERT is already
 * excluded from revenus/depenses everywhere, so combining accounts never double-counts money that
 * moved from one of the household's own accounts to another).
 *
 * <p>{@code savingsRate} = totalEpargne / totalRevenus (0 when totalRevenus is 0) - documented
 * here since the spec asks this figure to never be ambiguous.
 */
public record YearlyAnalysisResponse(
        int year,
        List<MonthlyResultResponse> monthlyResults,
        BigDecimal totalRevenus,
        BigDecimal totalDepenses,
        BigDecimal totalResultat,
        BigDecimal totalEpargne,
        /** Money taken back out of the livrets (Provision excluded), as a positive amount. */
        BigDecimal totalEpargneUtilisee,
        BigDecimal savingsRate,
        List<CategoryYearSummaryResponse> categoryBreakdown,
        List<MonthlyCategoryAmountResponse> evolutionParCategorie,
        List<SavingsAccountSummaryResponse> epargneParCompte) {
}
