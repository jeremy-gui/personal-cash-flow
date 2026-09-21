package com.personalcashflow.dashboard.dto;

import com.personalcashflow.budget.dto.BudgetResponse;
import com.personalcashflow.transaction.dto.TransactionResponse;
import java.math.BigDecimal;
import java.util.List;

public record DashboardResponse(
        String month,
        BigDecimal revenus,
        BigDecimal depenses,
        BigDecimal soldeNet,
        BigDecimal soldeReelFinDeMois,
        BigDecimal soldeActuel,
        /** Always the real current month's projection - paired with soldeActuel, a "right now" figure. */
        BigDecimal previsionnelFinDeMois,
        /** The projection for {@code month} specifically, which may differ from the real current month. */
        BigDecimal previsionnelFinDeMoisSelectionne,
        List<CategoryAmountResponse> depensesParCategorie,
        List<BudgetResponse> budgetsParCategorie,
        List<TransactionResponse> previsionnellesNonPointees) {
}
