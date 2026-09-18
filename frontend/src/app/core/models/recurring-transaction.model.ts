import { TransactionType } from './transaction.model';

export interface RecurringTransaction {
  id: number;
  label: string;
  amount: number;
  type: TransactionType;
  categoryId: number;
  categoryName: string;
  startMonth: string;
}

/** amount is the positive magnitude entered by the user; the backend derives the sign from type. */
export interface RecurringTransactionRequest {
  label: string;
  amount: number;
  type: TransactionType;
  categoryId: number;
}
