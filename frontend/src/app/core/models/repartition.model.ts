export type RepartitionLineKind = 'SALAIRE' | 'FRAIS' | 'AIDE';

/** A hand-entered input of the split: a salary, or a frais/aide that isn't a recurring transaction. */
export interface RepartitionLine {
  id: number;
  kind: RepartitionLineKind;
  label: string;
  amount: number;
}

export type RepartitionLineRequest = Omit<RepartitionLine, 'id'>;

/** Real average monthly net frais over this year's complete months (from/to: yyyy-MM). */
export interface AverageFrais {
  months: number;
  from: string;
  to: string;
  average: number;
}

export interface PersonShare {
  name: string;
  salary: number;
  /** Share of the combined salaries, 0..1. */
  ratio: number;
  /** What this person transfers to the joint account each month, rounded to the euro. */
  contribution: number;
  resteAVivre: number;
}

export interface RepartitionResult {
  fraisTotal: number;
  aidesTotal: number;
  /** Frais still to cover once the aides are deducted - what the salaries split between them. */
  netFrais: number;
  people: PersonShare[];
}

/**
 * Same formula as the "calcul répartition" spreadsheet tab: each person covers the frais left after
 * aides in proportion to their salary (ratio = salary / sum of salaries).
 */
export function computeRepartition(
  salaries: { name: string; amount: number }[],
  frais: number[],
  aides: number[],
): RepartitionResult {
  const sum = (values: number[]) => values.reduce((total, value) => total + value, 0);
  const fraisTotal = sum(frais);
  const aidesTotal = sum(aides);
  const netFrais = fraisTotal - aidesTotal;
  const salaryTotal = sum(salaries.map((s) => s.amount));
  const people = salaries.map(({ name, amount }) => {
    const ratio = salaryTotal > 0 ? amount / salaryTotal : 0;
    const contribution = Math.round(ratio * netFrais);
    return { name, salary: amount, ratio, contribution, resteAVivre: amount - contribution };
  });
  return { fraisTotal, aidesTotal, netFrais, people };
}
