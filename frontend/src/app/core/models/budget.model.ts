export interface Budget {
  id: number;
  categoryId: number;
  categoryName: string;
  startMonth: string;
  amount: number;
  spent: number;
  remaining: number;
  percentageConsumed: number;
}

export interface BudgetRequest {
  categoryId: number;
  month: string;
  amount: number;
}
