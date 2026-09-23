export interface MonthlyResult {
  month: number;
  revenus: number;
  depenses: number;
  resultat: number;
  epargne: number;
}

export interface CategoryYearSummary {
  categoryId: number;
  categoryName: string;
  amount: number;
  partOfRevenue: number;
  currentMonthBudget: number | null;
  currentMonthActual: number | null;
  currentMonthEcart: number | null;
}

export interface MonthlyCategoryAmount {
  month: number;
  categoryId: number;
  categoryName: string;
  amount: number;
}

export interface SavingsAccountSummary {
  accountId: number;
  accountName: string;
  contributed: number;
}

export interface YearlyAnalysis {
  year: number;
  monthlyResults: MonthlyResult[];
  totalRevenus: number;
  totalDepenses: number;
  totalResultat: number;
  totalEpargne: number;
  savingsRate: number;
  categoryBreakdown: CategoryYearSummary[];
  evolutionParCategorie: MonthlyCategoryAmount[];
  epargneParCompte: SavingsAccountSummary[];
}
