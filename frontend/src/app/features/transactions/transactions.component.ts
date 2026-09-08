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
  protected readonly editingId = signal<number | null>(null);
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
    status: this.fb.nonNullable.control<TransactionStatus>('POINTEE'),
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
    this.editingId.set(transaction.id);
    this.form.setValue({
      date: transaction.date,
      label: transaction.label,
      amount: Math.abs(transaction.amount),
      accountId: transaction.accountId,
      categoryId: transaction.categoryId,
      type: transaction.type,
      status: transaction.status,
    });
  }

  protected cancelEdit(): void {
    this.editingId.set(null);
    this.form.reset({
      date: this.today(),
      label: '',
      amount: 0,
      accountId: null,
      categoryId: null,
      type: 'DEPENSE',
      status: 'POINTEE',
    });
  }

  protected submit(): void {
    if (this.form.invalid) {
      return;
    }
    const raw = this.form.getRawValue();
    const request: TransactionRequest = { ...raw, accountId: raw.accountId! };
    const id = this.editingId();
    const request$ = id
      ? this.transactionService.update(id, request)
      : this.transactionService.create(request);

    request$.subscribe(() => {
      this.cancelEdit();
      this.reload();
    });
  }

  protected remove(transaction: Transaction): void {
    if (!confirm(`Supprimer la transaction "${transaction.label}" ?`)) {
      return;
    }
    this.transactionService.delete(transaction.id).subscribe(() => this.reload());
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
