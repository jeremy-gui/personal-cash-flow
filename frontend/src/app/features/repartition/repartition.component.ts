import { DecimalPipe, NgTemplateOutlet, PercentPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { RecurringTransaction } from '../../core/models/recurring-transaction.model';
import {
  RepartitionLine,
  RepartitionLineKind,
  computeRepartition,
} from '../../core/models/repartition.model';
import { RecurringTransactionService } from '../budgets/recurring-transaction.service';
import { RepartitionService } from './repartition.service';

/** Recurring revenus in this category are the transfers this page computes, not aides. */
const SALARY_CATEGORY = 'Salaire';

@Component({
  selector: 'app-repartition',
  imports: [DecimalPipe, NgTemplateOutlet, PercentPipe],
  templateUrl: './repartition.component.html',
  styleUrl: './repartition.component.css',
})
export class RepartitionComponent {
  private readonly repartitionService = inject(RepartitionService);
  private readonly recurringService = inject(RecurringTransactionService);

  protected readonly lines = signal<RepartitionLine[]>([]);
  protected readonly recurring = signal<RecurringTransaction[]>([]);
  /** Page-only tweaks of a recurring amount (not saved): the base stays the recurring transaction. */
  protected readonly overrides = signal<Record<number, number>>({});
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly abs = Math.abs;

  protected readonly salaries = computed(() => this.lines().filter((l) => l.kind === 'SALAIRE'));
  protected readonly manualFrais = computed(() => this.lines().filter((l) => l.kind === 'FRAIS'));
  protected readonly manualAides = computed(() => this.lines().filter((l) => l.kind === 'AIDE'));

  protected readonly recurringFrais = computed(() =>
    this.recurring().filter((r) => r.type === 'DEPENSE' || r.type === 'TRANSFERT'),
  );
  protected readonly recurringAides = computed(() =>
    this.recurring().filter((r) => r.type === 'REVENU' && r.categoryName !== SALARY_CATEGORY),
  );
  private readonly recurringSalaries = computed(() =>
    this.recurring().filter((r) => r.type === 'REVENU' && r.categoryName === SALARY_CATEGORY),
  );

  protected readonly result = computed(() =>
    computeRepartition(
      this.salaries().map((s) => ({ name: s.label, amount: s.amount })),
      [...this.recurringFrais().map((r) => this.amountOf(r)), ...this.manualFrais().map((l) => l.amount)],
      [...this.recurringAides().map((r) => this.amountOf(r)), ...this.manualAides().map((l) => l.amount)],
    ),
  );

  constructor() {
    this.repartitionService.list().subscribe((lines) => this.lines.set(lines));
    this.recurringService.list().subscribe((recurring) => this.recurring.set(recurring));
  }

  protected amountOf(recurring: RecurringTransaction): number {
    return this.overrides()[recurring.id] ?? Math.abs(recurring.amount);
  }

  protected isOverridden(recurring: RecurringTransaction): boolean {
    return recurring.id in this.overrides();
  }

  protected overrideAmount(recurring: RecurringTransaction, value: string): void {
    this.overrides.update((o) => ({ ...o, [recurring.id]: this.parse(value) }));
  }

  protected resetAmount(recurring: RecurringTransaction): void {
    this.overrides.update(({ [recurring.id]: _, ...rest }) => rest);
  }

  /** The recurring "alimentation <name>" (category Salaire) currently planned for this person, if any. */
  protected plannedFor(name: string): RecurringTransaction | null {
    const needle = name.toLowerCase();
    return this.recurringSalaries().find((r) => r.label.toLowerCase().includes(needle)) ?? null;
  }

  /** Live recompute while typing; saved on change (blur/enter). */
  protected editLine(line: RepartitionLine, patch: Partial<RepartitionLine>): void {
    this.lines.update((lines) => lines.map((l) => (l.id === line.id ? { ...l, ...patch } : l)));
  }

  protected saveLine(id: number): void {
    const line = this.lines().find((l) => l.id === id);
    if (!line || !line.label.trim()) {
      return;
    }
    this.repartitionService
      .update(id, { kind: line.kind, label: line.label.trim(), amount: line.amount })
      .subscribe({ error: (err: HttpErrorResponse) => this.showError(err) });
  }

  protected addLine(kind: RepartitionLineKind): void {
    const label = kind === 'SALAIRE' ? 'Nouvelle personne' : 'Nouvelle ligne';
    this.repartitionService.create({ kind, label, amount: 0 }).subscribe({
      next: (line) => this.lines.update((lines) => [...lines, line]),
      error: (err: HttpErrorResponse) => this.showError(err),
    });
  }

  protected deleteLine(id: number): void {
    this.repartitionService.delete(id).subscribe({
      next: () => this.lines.update((lines) => lines.filter((l) => l.id !== id)),
      error: (err: HttpErrorResponse) => this.showError(err),
    });
  }

  protected parse(value: string): number {
    const amount = Number(String(value).replace(',', '.'));
    return Number.isFinite(amount) && amount >= 0 ? amount : 0;
  }

  private showError(err: HttpErrorResponse): void {
    this.errorMessage.set(err.error?.message ?? 'Une erreur est survenue.');
  }
}
