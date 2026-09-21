export type TransactionType = 'DEPENSE' | 'REVENU';
export type TransactionStatus = 'PREVISIONNELLE' | 'POINTEE';
export type TransactionSource = 'MANUAL' | 'IMPORT';

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
  source: TransactionSource;
  originalDate: string | null;
  originalLabel: string | null;
  /** True once this pointee has already absorbed a previsionnelle via reconciliation. */
  matched: boolean;
  /** False while the category was auto-suggested from import history and not yet reviewed. */
  categoryConfirmed: boolean;
  /** False while the label was auto-suggested from import history and not yet reviewed. */
  labelConfirmed: boolean;
}

/**
 * amount is the positive magnitude entered by the user; the backend derives the sign from type.
 * There is no status: manual entries are always PREVISIONNELLE. POINTEE only comes from a bank
 * import or from reconciling a previsionnelle against one.
 */
export interface TransactionRequest {
  date: string;
  label: string;
  amount: number;
  accountId: number;
  categoryId: number | null;
  type: TransactionType;
}

export interface TransactionFilters {
  month: string | null;
  accountId: number | null;
  categoryId: number | null;
  status: TransactionStatus | null;
  type: TransactionType | null;
}
