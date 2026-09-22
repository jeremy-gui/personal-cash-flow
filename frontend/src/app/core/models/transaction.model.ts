export type TransactionType = 'DEPENSE' | 'REVENU' | 'TRANSFERT';
export type TransactionStatus = 'PREVISIONNELLE' | 'POINTEE';
export type TransactionSource = 'MANUAL' | 'IMPORT' | 'TRANSFER';

export const TRANSACTION_TYPE_LABELS: Record<TransactionType, string> = {
  DEPENSE: 'Dépense',
  REVENU: 'Revenu',
  TRANSFERT: 'Transfert',
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
  /** The other account of a TRANSFERT pair; null for DEPENSE/REVENU. */
  transferAccountId: number | null;
  transferAccountName: string | null;
}

/**
 * amount is the positive magnitude entered by the user; the backend derives the sign from type
 * (DEPENSE/REVENU) or from outgoing (TRANSFERT only - true if money leaves this account).
 * transferAccountId is required when type is TRANSFERT, ignored otherwise.
 * There is no status: manual entries are always PREVISIONNELLE. POINTEE only comes from a bank
 * import, a reconciliation, or the mirror side of a transfer.
 */
export interface TransactionRequest {
  date: string;
  label: string;
  amount: number;
  accountId: number;
  categoryId: number | null;
  type: TransactionType;
  transferAccountId: number | null;
  outgoing: boolean;
}

export interface TransactionFilters {
  month: string | null;
  accountId: number | null;
  categoryId: number | null;
  status: TransactionStatus | null;
  type: TransactionType | null;
}
