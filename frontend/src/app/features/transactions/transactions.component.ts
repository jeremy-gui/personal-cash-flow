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
import { TransactionService } from './transaction.service';

function currentMonth(): string {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
}

@Component({
  selector: 'app-transactions',
  imports: [CommonModule, ReactiveFormsModule],
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
  protected readonly previsionnelleTransactions = computed(() =>
    this.transactions().filter((t) => t.status === 'PREVISIONNELLE'),
  );
  protected readonly pointeeTransactions = computed(() =>
    this.transactions().filter((t) => t.status === 'POINTEE'),
  );
  protected readonly editingId = signal<number | null>(null);
  protected readonly pendingDeleteId = signal<number | null>(null);
  protected readonly matchError = signal<string | null>(null);

  protected readonly month = signal(currentMonth());
  protected readonly filterAccountId = signal<number | null>(null);
  protected readonly filterCategoryId = signal<number | null>(null);
  protected readonly filterStatus = signal<TransactionStatus | null>(null);
  protected readonly filterType = signal<TransactionType | null>(null);

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

  constructor() {
    this.accountService.list().subscribe((accounts) => this.accounts.set(accounts));
    this.categoryService.list().subscribe((categories) => this.categories.set(categories));
    this.reload();
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
        accountId: null,
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

  /** Candidates to reconcile with: same account, opposite status. */
  protected candidatesFor(transaction: Transaction): Transaction[] {
    return this.transactions().filter(
      (candidate) =>
        candidate.id !== transaction.id &&
        candidate.accountId === transaction.accountId &&
        candidate.status !== transaction.status,
    );
  }

  /** Wraps the Record lookup so it stays typed when called from the shared row ng-template,
   *  whose ngTemplateOutletContext variables aren't inferred by the template type checker. */
  protected typeLabel(type: TransactionType): string {
    return this.typeLabels[type];
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

  /** Merges the previsionnelle into its pointee counterpart; the previsionnelle row disappears. */
  protected match(transaction: Transaction, matchWithId: number | null): void {
    if (matchWithId === null) {
      return;
    }
    this.matchError.set(null);
    this.transactionService.match(transaction.id, matchWithId).subscribe({
      next: () => this.reload(),
      error: (err: HttpErrorResponse) => this.matchError.set(err.error?.message ?? 'Une erreur est survenue.'),
    });
  }
}
