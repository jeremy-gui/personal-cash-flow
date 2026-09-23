export interface Provision {
  id: number;
  name: string;
  targetAmount: number;
  targetDate: string | null;
  categoryId: number | null;
  categoryName: string | null;
  accountId: number | null;
  accountName: string | null;
  active: boolean;
  currentAmount: number;
  remaining: number;
  percentageComplete: number;
  monthlyEffortNeeded: number | null;
  atRisk: boolean;
}

export interface ProvisionRequest {
  name: string;
  targetAmount: number;
  targetDate: string | null;
  categoryId: number | null;
  accountId: number | null;
  active: boolean;
}

export interface ProvisionContribution {
  id: number;
  amount: number;
  date: string;
}

export interface ProvisionContributionRequest {
  amount: number;
  date: string;
}
