import { describe, expect, it } from 'vitest';
import { computeRepartition } from './repartition.model';

describe('computeRepartition', () => {
  it('matches the "calcul répartition" spreadsheet', () => {
    // Spreadsheet: TOTAL FRAIS 4100, aides 100 -> 4000, Alex 2000 / Sam 3000.
    const result = computeRepartition(
      [
        { name: 'Alex', amount: 2000 },
        { name: 'Sam', amount: 3000 },
      ],
      [4100],
      [100],
    );

    expect(result.netFrais).toBeCloseTo(4000, 2);
    // 1600 / 2400 in the spreadsheet, rounded to the euro like the actual transfers.
    expect(result.people[0].contribution).toBe(1600);
    expect(result.people[1].contribution).toBe(2400);
    expect(result.people[0].resteAVivre).toBe(400);
    expect(result.people[1].resteAVivre).toBe(600);
  });

  it('splits nothing when no salary is entered', () => {
    const result = computeRepartition([{ name: 'Alex', amount: 0 }], [100], []);

    expect(result.people[0].contribution).toBe(0);
  });
});
