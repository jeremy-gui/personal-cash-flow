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
        BigDecimal previsionnelFinDeMois,
        List<CategoryAmountResponse> depensesParCategorie,
        List<BudgetResponse> budgetsParCategorie,
        List<TransactionResponse> previsionnellesNonPointees) {
}
