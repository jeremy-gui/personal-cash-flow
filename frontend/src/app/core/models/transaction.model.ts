export type TransactionType = 'DEPENSE' | 'REVENU';
export type TransactionStatus = 'PREVISIONNELLE' | 'POINTEE';

export const TRANSACTION_TYPE_LABELS: Record<TransactionType, string> = {
  DEPENSE: 'Dépense',
  REVENU: 'Revenu',
};

export const TRANSACTION_STATUS_LABELS: Record<TransactionStatus, string> = {
  PREVISIONNELLE: 'Prévisionnelle',
  POINTEE: 'Pointée',
};

export interface Transaction {
  id: number;
  date: string;
  label: string;
  amount: number;
  accountId: number;
  accountName: string;
  categoryId: number | null;
  categoryName: string | null;
  type: TransactionType;
  status: TransactionStatus;
}

/** amount is the positive magnitude entered by the user; the backend derives the sign from type. */
export interface TransactionRequest {
  date: string;
  label: string;
  amount: number;
  accountId: number;
  categoryId: number | null;
  type: TransactionType;
  status: TransactionStatus;
}

export interface TransactionFilters {
  month: string | null;
  accountId: number | null;
  categoryId: number | null;
  status: TransactionStatus | null;
  type: TransactionType | null;
}
