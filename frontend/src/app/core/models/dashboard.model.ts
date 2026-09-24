import { Budget } from './budget.model';
import { Transaction } from './transaction.model';

export interface CategoryAmount {
  /** null for the "Non catégorisées" slice. */
  categoryId: number | null;
  categoryName: string;
  amount: number;
}

export interface Dashboard {
  month: string;
  revenus: number;
  depenses: number;
  soldeNet: number;
  soldeReelFinDeMois: number;
  soldeActuel: number;
  previsionnelFinDeMois: number;
  previsionnelFinDeMoisSelectionne: number;
  depensesParCategorie: CategoryAmount[];
  budgetsParCategorie: Budget[];
  previsionnellesNonPointees: Transaction[];
}
