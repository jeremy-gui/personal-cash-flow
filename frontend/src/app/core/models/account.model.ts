export type AccountType = 'COURANT' | 'JOINT' | 'LIVRET' | 'AUTRE';

export const ACCOUNT_TYPE_LABELS: Record<AccountType, string> = {
  COURANT: 'Compte courant',
  JOINT: 'Compte joint',
  LIVRET: 'Livret',
  AUTRE: 'Autre',
};

export interface Account {
  id: number;
  name: string;
  type: AccountType;
  initialBalance: number;
  accountNumber: string | null;
  isDefault: boolean;
  balance: number;
}

export interface AccountRequest {
  name: string;
  type: AccountType;
  initialBalance: number;
  accountNumber: string | null;
  isDefault: boolean;
}
