import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Budget, BudgetRequest } from '../../core/models/budget.model';
import { buildCategoryTree, Category } from '../../core/models/category.model';
import { RecurringTransaction, RecurringTransactionRequest } from '../../core/models/recurring-transaction.model';
import { TRANSACTION_TYPE_LABELS, TransactionType } from '../../core/models/transaction.model';
import { CategoryService } from '../categories/category.service';
import { BudgetService } from './budget.service';
import { RecurringTransactionService } from './recurring-transaction.service';

function currentMonth(): string {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
}

@Component({
  selector: 'app-budgets',
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './budgets.component.html',
  styleUrl: './budgets.component.css',
})
export class BudgetsComponent {
  private readonly budgetService = inject(BudgetService);
  private readonly categoryService = inject(CategoryService);
  private readonly recurringTransactionService = inject(RecurringTransactionService);
  private readonly fb = inject(FormBuilder);

  protected readonly month = signal(currentMonth());
  protected readonly budgets = signal<Budget[]>([]);
  protected readonly categories = signal<Category[]>([]);
  protected readonly editingId = signal<number | null>(null);
  protected readonly pendingDeleteId = signal<number | null>(null);
  protected readonly errorMessage = signal<string | null>(null);

  protected readonly categoryTree = computed(() => buildCategoryTree(this.categories()));
  protected readonly availableCategoryIds = computed(() => new Set(this.availableCategories().map((c) => c.id)));
  private readonly availableCategories = computed(() => {
    const editing = this.budgets().find((b) => b.id === this.editingId());
    const budgetedIds = new Set(
      this.budgets()
        .filter((b) => b.id !== this.editingId())
        .map((b) => b.categoryId),
    );
    return this.categories().filter(
      (c) => !budgetedIds.has(c.id) || c.id === editing?.categoryId,
    );
  });

  protected readonly form = this.fb.nonNullable.group({
    categoryId: this.fb.control<number | null>(null, Validators.required),
    amount: [0, [Validators.required, Validators.min(0.01)]],
  });

  protected readonly recurringTransactions = signal<RecurringTransaction[]>([]);
  protected readonly recurringErrorMessage = signal<string | null>(null);
  protected readonly pendingDeleteRecurringId = signal<number | null>(null);
  protected readonly typeLabels = TRANSACTION_TYPE_LABELS;
  protected readonly types = Object.keys(TRANSACTION_TYPE_LABELS) as TransactionType[];

  protected readonly recurringForm = this.fb.nonNullable.group({
    label: ['', Validators.required],
    categoryId: this.fb.control<number | null>(null, Validators.required),
    amount: [0, [Validators.required, Validators.min(0.01)]],
    type: this.fb.nonNullable.control<TransactionType>('DEPENSE'),
  });

  constructor() {
    this.categoryService.list().subscribe((categories) => this.categories.set(categories));
    this.reload();
    this.reloadRecurring();
  }

  private reload(): void {
    this.budgetService.listForMonth(this.month()).subscribe((budgets) => this.budgets.set(budgets));
  }

  protected onMonthChange(value: string): void {
    this.month.set(value);
    this.cancelEdit();
    this.pendingDeleteId.set(null);
    this.reload();
  }

  protected edit(budget: Budget): void {
    this.pendingDeleteId.set(null);
    this.editingId.set(budget.id);
    this.errorMessage.set(null);
    this.form.setValue({ categoryId: budget.categoryId, amount: budget.amount });
  }

  protected cancelEdit(): void {
    this.editingId.set(null);
    this.form.reset({ categoryId: null, amount: 0 });
  }

  protected submit(): void {
    if (this.form.invalid) {
      return;
    }
    this.errorMessage.set(null);
    const raw = this.form.getRawValue();
    const request: BudgetRequest = { categoryId: raw.categoryId!, month: this.month(), amount: raw.amount };
    const id = this.editingId();
    const request$ = id ? this.budgetService.update(id, request) : this.budgetService.create(request);

    request$.subscribe({
      next: () => {
        this.cancelEdit();
        this.reload();
      },
      error: (err: HttpErrorResponse) => this.errorMessage.set(err.error?.message ?? 'Une erreur est survenue.'),
    });
  }

  protected requestDelete(budgetId: number): void {
    this.editingId.set(null);
    this.pendingDeleteId.set(budgetId);
  }

  protected cancelDelete(): void {
    this.pendingDeleteId.set(null);
  }

  protected confirmDelete(budgetId: number): void {
    this.budgetService.delete(budgetId).subscribe(() => {
      this.pendingDeleteId.set(null);
      this.reload();
    });
  }

  private reloadRecurring(): void {
    this.recurringTransactionService.list().subscribe((recurring) => this.recurringTransactions.set(recurring));
  }

  protected submitRecurring(): void {
    if (this.recurringForm.invalid) {
      return;
    }
    this.recurringErrorMessage.set(null);
    const raw = this.recurringForm.getRawValue();
    const request: RecurringTransactionRequest = {
      label: raw.label,
      categoryId: raw.categoryId!,
      amount: raw.amount,
      type: raw.type,
    };

    this.recurringTransactionService.create(request).subscribe({
      next: () => {
        this.recurringForm.reset({ label: '', categoryId: null, amount: 0, type: 'DEPENSE' });
        this.reloadRecurring();
      },
      error: (err: HttpErrorResponse) => this.recurringErrorMessage.set(err.error?.message ?? 'Une erreur est survenue.'),
    });
  }

  protected requestDeleteRecurring(id: number): void {
    this.pendingDeleteRecurringId.set(id);
  }

  protected cancelDeleteRecurring(): void {
    this.pendingDeleteRecurringId.set(null);
  }

  protected confirmDeleteRecurring(id: number): void {
    this.recurringTransactionService.delete(id).subscribe(() => {
      this.pendingDeleteRecurringId.set(null);
      this.reloadRecurring();
    });
  }
}
