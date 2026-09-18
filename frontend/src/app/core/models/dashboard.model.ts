import { Budget } from './budget.model';
import { Transaction } from './transaction.model';

export interface CategoryAmount {
  categoryId: number;
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
  depensesParCategorie: CategoryAmount[];
  budgetsParCategorie: Budget[];
  previsionnellesNonPointees: Transaction[];
}
