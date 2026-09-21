import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Account } from '../../core/models/account.model';
import { buildCategoryTree, Category } from '../../core/models/category.model';
import {
  TRANSACTION_STATUS_LABELS,
  TRANSACTION_TYPE_LABELS,
  Transaction,
  TransactionRequest,
  TransactionStatus,
  TransactionType,
} from '../../core/models/transaction.model';
import { AccountService } from '../accounts/account.service';
import { CategoryService } from '../categories/category.service';
import { MatchPickerComponent } from './match-picker.component';
import { TransactionService } from './transaction.service';

function currentMonth(): string {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
}

function normalizeText(value: string): string {
  return value
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase();
}

function normalizeTokens(label: string): Set<string> {
  return new Set(
    normalizeText(label)
      .split(/[^a-z0-9]+/)
      .filter((token) => token.length > 1),
  );
}

/** Fraction of `a`'s tokens found in `b` - simple, dependency-free, mirrors the backend's OFX label matching. */
function labelSimilarity(a: string, b: string): number {
  const tokensA = normalizeTokens(a);
  if (tokensA.size === 0) {
    return 0;
  }
  const tokensB = normalizeTokens(b);
  let overlap = 0;
  for (const token of tokensA) {
    if (tokensB.has(token)) {
      overlap++;
    }
  }
  return overlap / tokensA.size;
}

function sameAmount(a: number, b: number): boolean {
  return Math.round(a * 100) === Math.round(b * 100);
}

type SortTable = 'previsionnelle' | 'pointee';
type SortColumn = 'date' | 'label' | 'category' | 'type' | 'amount';
type SortDirection = 'asc' | 'desc';

interface SortState {
  column: SortColumn;
  direction: SortDirection;
}

function compareByColumn(a: Transaction, b: Transaction, column: SortColumn): number {
  switch (column) {
    case 'date':
      return a.date.localeCompare(b.date);
    case 'label':
      return a.label.localeCompare(b.label);
    case 'category':
      return (a.categoryName ?? '').localeCompare(b.categoryName ?? '');
    case 'type':
      return a.type.localeCompare(b.type);
    case 'amount':
      return a.amount - b.amount;
  }
}

function sortTransactions(transactions: Transaction[], sort: SortState | null): Transaction[] {
  if (!sort) {
    return transactions;
  }
  const factor = sort.direction === 'asc' ? 1 : -1;
  return [...transactions].sort((a, b) => factor * compareByColumn(a, b, sort.column));
}

@Component({
  selector: 'app-transactions',
  imports: [CommonModule, ReactiveFormsModule, MatchPickerComponent],
  templateUrl: './transactions.component.html',
  styleUrl: './transactions.component.css',
})
export class TransactionsComponent {
  private readonly transactionService = inject(TransactionService);
  private readonly accountService = inject(AccountService);
  private readonly categoryService = inject(CategoryService);
  private readonly fb = inject(FormBuilder);

  protected readonly statusLabels = TRANSACTION_STATUS_LABELS;
  protected readonly typeLabels = TRANSACTION_TYPE_LABELS;
  protected readonly statuses = Object.keys(TRANSACTION_STATUS_LABELS) as TransactionStatus[];
  protected readonly types = Object.keys(TRANSACTION_TYPE_LABELS) as TransactionType[];

  protected readonly accounts = signal<Account[]>([]);
  protected readonly categories = signal<Category[]>([]);
  protected readonly categoryTree = computed(() => buildCategoryTree(this.categories()));
  protected readonly transactions = signal<Transaction[]>([]);
  protected readonly previsionnelleSort = signal<SortState | null>(null);
  protected readonly pointeeSort = signal<SortState | null>(null);
  protected readonly previsionnelleTransactions = computed(() =>
    sortTransactions(
      this.transactions().filter((t) => t.status === 'PREVISIONNELLE'),
      this.previsionnelleSort(),
    ),
  );
  protected readonly pointeeTransactions = computed(() =>
    sortTransactions(
      this.transactions().filter((t) => t.status === 'POINTEE'),
      this.pointeeSort(),
    ),
  );
  protected readonly previsionnelleTotal = computed(() =>
    this.previsionnelleTransactions().reduce((sum, t) => sum + t.amount, 0),
  );
  protected readonly pointeeTotal = computed(() => this.pointeeTransactions().reduce((sum, t) => sum + t.amount, 0));
  protected readonly suggestedCategoryIds = computed(() =>
    this.pointeeTransactions()
      .filter((t) => t.categoryId !== null && !t.categoryConfirmed)
      .map((t) => t.id),
  );
  protected readonly editingId = signal<number | null>(null);
  protected readonly pendingDeleteId = signal<number | null>(null);
  protected readonly pendingConfirmAllCategories = signal(false);
  protected readonly matchError = signal<string | null>(null);

  protected readonly month = signal(currentMonth());
  protected readonly filterAccountId = signal<number | null>(null);
  protected readonly filterCategoryId = signal<number | null>(null);
  protected readonly filterStatus = signal<TransactionStatus | null>(null);
  protected readonly filterType = signal<TransactionType | null>(null);

  /** The real bank balance (initial + pointées): the filtered account's, or the sum of all when "Tous" is selected. */
  protected readonly bankBalance = computed(() => {
    const id = this.filterAccountId();
    const accounts = this.accounts();
    if (id === null) {
      return accounts.reduce((sum, account) => sum + account.balance, 0);
    }
    return accounts.find((account) => account.id === id)?.balance ?? 0;
  });

  protected readonly bankBalanceLabel = computed(() => {
    const id = this.filterAccountId();
    if (id === null) {
      return 'Solde total';
    }
    const name = this.accounts().find((account) => account.id === id)?.name;
    return name ? `Solde ${name}` : 'Solde';
  });

  protected readonly form = this.fb.nonNullable.group({
    date: [this.today(), Validators.required],
    label: ['', Validators.required],
    amount: [0, [Validators.required, Validators.min(0.01)]],
    accountId: this.fb.control<number | null>(null, Validators.required),
    categoryId: this.fb.control<number | null>(null),
    type: this.fb.nonNullable.control<TransactionType>('DEPENSE'),
  });

  /** Populated per-row when a transaction is being edited in-line in the table. */
  protected readonly editForm = this.fb.nonNullable.group({
    date: ['', Validators.required],
    label: ['', Validators.required],
    amount: [0, [Validators.required, Validators.min(0.01)]],
    accountId: this.fb.control<number | null>(null, Validators.required),
    categoryId: this.fb.control<number | null>(null),
    type: this.fb.nonNullable.control<TransactionType>('DEPENSE'),
  });

  private defaultAccountId: number | null = null;

  constructor() {
    this.accountService.list().subscribe((accounts) => {
      this.accounts.set(accounts);
      this.defaultAccountId = accounts.find((a) => a.isDefault)?.id ?? null;
      this.form.controls.accountId.setValue(this.defaultAccountId);
      this.filterAccountId.set(this.defaultAccountId);
      this.reload();
    });
    this.categoryService.list().subscribe((categories) => this.categories.set(categories));
  }

  private today(): string {
    return new Date().toISOString().slice(0, 10);
  }

  protected reload(): void {
    this.transactionService
      .search({
        month: this.month(),
        accountId: this.filterAccountId(),
        categoryId: this.filterCategoryId(),
        status: this.filterStatus(),
        type: this.filterType(),
      })
      .subscribe((transactions) => this.transactions.set(transactions));
    // Re-fetch balances too, so the badge stays live as transactions are added/edited/deleted/matched.
    this.accountService.list().subscribe((accounts) => this.accounts.set(accounts));
    // The suggested-categories count is about to change under it - don't leave a stale confirm prompt open.
    this.pendingConfirmAllCategories.set(false);
  }

  protected onMonthChange(value: string): void {
    this.month.set(value);
    this.reload();
  }

  protected shiftMonth(delta: number): void {
    const [year, month] = this.month().split('-').map(Number);
    const date = new Date(year, month - 1 + delta, 1);
    this.onMonthChange(`${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`);
  }

  protected onFilterAccountChange(value: string): void {
    this.filterAccountId.set(value ? Number(value) : null);
    this.reload();
  }

  protected onFilterCategoryChange(value: string): void {
    this.filterCategoryId.set(value ? Number(value) : null);
    this.reload();
  }

  protected onFilterStatusChange(value: string): void {
    this.filterStatus.set((value as TransactionStatus) || null);
    this.reload();
  }

  protected onFilterTypeChange(value: string): void {
    this.filterType.set((value as TransactionType) || null);
    this.reload();
  }

  protected edit(transaction: Transaction): void {
    this.pendingDeleteId.set(null);
    this.editingId.set(transaction.id);
    this.editForm.setValue({
      date: transaction.date,
      label: transaction.label,
      amount: Math.abs(transaction.amount),
      accountId: transaction.accountId,
      categoryId: transaction.categoryId,
      type: transaction.type,
    });
  }

  protected cancelEdit(): void {
    this.editingId.set(null);
  }

  protected setEditType(type: TransactionType): void {
    this.editForm.controls.type.setValue(type);
  }

  protected submit(): void {
    if (this.form.invalid) {
      return;
    }
    const raw = this.form.getRawValue();
    const request: TransactionRequest = { ...raw, accountId: raw.accountId! };
    this.transactionService.create(request).subscribe(() => {
      this.form.reset({
        date: this.today(),
        label: '',
        amount: 0,
        accountId: this.defaultAccountId,
        categoryId: null,
        type: 'DEPENSE',
      });
      this.reload();
    });
  }

  protected submitEdit(): void {
    if (this.editForm.invalid) {
      return;
    }
    const id = this.editingId();
    if (id === null) {
      return;
    }
    const raw = this.editForm.getRawValue();
    const request: TransactionRequest = { ...raw, accountId: raw.accountId! };
    this.transactionService.update(id, request).subscribe(() => {
      this.editingId.set(null);
      this.reload();
    });
  }

  protected requestDelete(transactionId: number): void {
    this.editingId.set(null);
    this.pendingDeleteId.set(transactionId);
  }

  protected cancelDelete(): void {
    this.pendingDeleteId.set(null);
  }

  protected confirmDelete(transactionId: number): void {
    this.transactionService.delete(transactionId).subscribe(() => {
      this.pendingDeleteId.set(null);
      this.reload();
    });
  }

  /** Candidates to reconcile with: same account, opposite status, not already matched. */
  protected candidatesFor(transaction: Transaction): Transaction[] {
    return this.transactions().filter(
      (candidate) =>
        candidate.id !== transaction.id &&
        candidate.accountId === transaction.accountId &&
        candidate.status !== transaction.status &&
        !candidate.matched,
    );
  }

  /**
   * Best-guess match to pre-select for validation: the unique same-amount candidate, or - when
   * several share that amount - the unique candidate whose label best overlaps this one's.
   * Returns null rather than guess when nothing stands out, so the user searches manually instead.
   */
  protected suggestedMatchFor(transaction: Transaction): Transaction | null {
    const candidates = this.candidatesFor(transaction).filter((candidate) =>
      sameAmount(candidate.amount, transaction.amount),
    );
    if (candidates.length === 0) {
      return null;
    }
    if (candidates.length === 1) {
      return candidates[0];
    }

    let best: Transaction | null = null;
    let bestScore = -1;
    let tieCount = 0;
    for (const candidate of candidates) {
      const score = Math.max(
        labelSimilarity(transaction.label, candidate.label),
        labelSimilarity(candidate.label, transaction.label),
      );
      if (score > bestScore) {
        bestScore = score;
        best = candidate;
        tieCount = 1;
      } else if (score === bestScore) {
        tieCount++;
      }
    }
    return bestScore > 0 && tieCount === 1 ? best : null;
  }

  /** Wraps the Record lookup so it stays typed when called from the shared row ng-template,
   *  whose ngTemplateOutletContext variables aren't inferred by the template type checker. */
  protected typeLabel(type: TransactionType): string {
    return this.typeLabels[type];
  }

  private sortSignal(table: SortTable) {
    return table === 'previsionnelle' ? this.previsionnelleSort : this.pointeeSort;
  }

  /** Cycles asc -> desc -> unsorted on repeated clicks of the same column. */
  protected toggleSort(table: SortTable, column: SortColumn): void {
    const sortSignal = this.sortSignal(table);
    const current = sortSignal();
    if (current?.column !== column) {
      sortSignal.set({ column, direction: 'asc' });
    } else if (current.direction === 'asc') {
      sortSignal.set({ column, direction: 'desc' });
    } else {
      sortSignal.set(null);
    }
  }

  protected sortIndicator(table: SortTable, column: SortColumn): string {
    const current = this.sortSignal(table)();
    if (current?.column !== column) {
      return '';
    }
    return current.direction === 'asc' ? '▲' : '▼';
  }

  protected ariaSort(table: SortTable, column: SortColumn): 'ascending' | 'descending' | 'none' {
    const current = this.sortSignal(table)();
    if (current?.column !== column) {
      return 'none';
    }
    return current.direction === 'asc' ? 'ascending' : 'descending';
  }

  protected hasOriginalDivergence(transaction: Transaction): boolean {
    return (
      transaction.originalLabel !== null &&
      (transaction.originalLabel !== transaction.label || transaction.originalDate !== transaction.date)
    );
  }

  protected originalTooltip(transaction: Transaction): string {
    const date = transaction.originalDate ? this.formatDate(transaction.originalDate) : '';
    return `Import d'origine : ${date} — ${transaction.originalLabel}`;
  }

  private formatDate(isoDate: string): string {
    const [year, month, day] = isoDate.split('-');
    return `${day}/${month}/${year}`;
  }

  /** Accepts a single auto-suggested category, making it permanent. */
  protected confirmCategory(transaction: Transaction): void {
    this.transactionService.confirmCategory(transaction.id).subscribe(() => this.reload());
  }

  protected requestConfirmAllCategories(): void {
    this.pendingConfirmAllCategories.set(true);
  }

  protected cancelConfirmAllCategories(): void {
    this.pendingConfirmAllCategories.set(false);
  }

  protected confirmAllCategories(): void {
    this.transactionService.confirmCategories(this.suggestedCategoryIds()).subscribe(() => {
      this.pendingConfirmAllCategories.set(false);
      this.reload();
    });
  }

  /** Merges the matched previsionnelle(s) into their pointee counterpart; the previsionnelle row(s) disappear. */
  protected match(transaction: Transaction, matchWithIds: number[]): void {
    if (matchWithIds.length === 0) {
      return;
    }
    this.matchError.set(null);
    this.transactionService.match(transaction.id, matchWithIds).subscribe({
      next: () => this.reload(),
      error: (err: HttpErrorResponse) => {
        this.matchError.set(err.error?.message ?? 'Une erreur est survenue.');
        // The request can fail after the backend already committed (e.g. a dropped connection) -
        // reload so a stale, already-matched pointee doesn't look selectable for a retry.
        this.reload();
      },
    });
  }
}
