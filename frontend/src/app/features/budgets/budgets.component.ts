import { CommonModule, formatDate } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, ElementRef, inject, signal, viewChild } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Account } from '../../core/models/account.model';
import { Budget, BudgetRequest } from '../../core/models/budget.model';
import { allowedTypesFor, buildCategoryTree, Category } from '../../core/models/category.model';
import { RecurringTransaction, RecurringTransactionRequest } from '../../core/models/recurring-transaction.model';
import { TRANSACTION_TYPE_LABELS, TransactionType } from '../../core/models/transaction.model';
import { AccountService } from '../accounts/account.service';
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
  private readonly accountService = inject(AccountService);
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

  private readonly recurringDialog = viewChild.required<ElementRef<HTMLDialogElement>>('recurringDialog');
  protected readonly editingRecurringId = signal<number | null>(null);
  private readonly recurringCategoryId = signal<number | null>(null);
  protected readonly recurringTypes = computed(() => allowedTypesFor(this.categories(), this.recurringCategoryId()));

  protected readonly recurringForm = this.fb.nonNullable.group({
    label: ['', Validators.required],
    categoryId: this.fb.control<number | null>(null, Validators.required),
    amount: [0, [Validators.required, Validators.min(0.01)]],
    type: this.fb.nonNullable.control<TransactionType>('DEPENSE'),
    variableAmount: this.fb.nonNullable.control(false),
    transferAccountId: this.fb.control<number | null>(null),
    outgoing: this.fb.nonNullable.control(true),
  });
  protected readonly accounts = signal<Account[]>([]);
  /** Mirrors the type control so the template shows the transfer fields only for a TRANSFERT. */
  protected readonly recurringType = signal<TransactionType>('DEPENSE');

  constructor() {
    this.categoryService.list().subscribe((categories) => this.categories.set(categories));
    this.accountService.list().subscribe((accounts) => this.accounts.set(accounts.filter((a) => !a.isDefault)));
    this.recurringForm.controls.type.valueChanges.subscribe((type) => this.recurringType.set(type));
    this.reload();
    this.reloadRecurring();
    // A recurring transaction is entered by hand too: only its category's allowed types, first one pre-selected.
    this.recurringForm.controls.categoryId.valueChanges.subscribe((categoryId) => {
      this.recurringCategoryId.set(categoryId);
      // A category with a compte cible is a transfer category: pre-fill it like the transaction form does.
      const account = this.categories().find((c) => c.id === categoryId)?.defaultTransferAccountId ?? null;
      const allowed = allowedTypesFor(this.categories(), categoryId);
      this.recurringForm.patchValue({
        type: account !== null && allowed.includes('TRANSFERT') ? 'TRANSFERT' : allowed[0],
        transferAccountId: account,
      });
    });
  }

  private reload(): void {
    this.budgetService.listForMonth(this.month()).subscribe((budgets) => this.budgets.set(budgets));
  }

  protected shiftMonth(delta: number): void {
    const [year, month] = this.month().split('-').map(Number);
    const date = new Date(year, month - 1 + delta, 1);
    this.onMonthChange(`${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`);
  }

  protected onMonthChange(value: string): void {
    this.month.set(value);
    this.pendingDeleteId.set(null);
    this.reload();
  }

  private readonly budgetDialog = viewChild.required<ElementRef<HTMLDialogElement>>('budgetDialog');
  protected readonly monthLabel = computed(() => formatDate(`${this.month()}-01`, 'MMMM y', 'fr-FR'));

  /** The form is (re)filled on every opening - never on the dialog's close event, unreliable after Escape. */
  protected openAdd(): void {
    this.openBudget(null, { categoryId: null, amount: 0 });
  }

  protected edit(budget: Budget): void {
    this.openBudget(budget.id, { categoryId: budget.categoryId, amount: budget.amount });
  }

  private openBudget(id: number | null, value: { categoryId: number | null; amount: number }): void {
    this.pendingDeleteId.set(null);
    this.errorMessage.set(null);
    this.editingId.set(id);
    this.form.reset(value);
    this.budgetDialog().nativeElement.showModal();
  }

  protected closeBudget(): void {
    this.budgetDialog().nativeElement.close();
  }

  /** Closes on a backdrop click. Must return void: a handler evaluating to `false` makes Angular
   *  preventDefault() every click inside the dialog, which breaks its submit button. */
  protected onBudgetDialogClick(event: MouseEvent): void {
    if (event.target === this.budgetDialog().nativeElement) {
      this.closeBudget();
    }
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
        this.closeBudget();
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

  /** The form is (re)filled on every opening - never on the dialog's close event, unreliable after Escape. */
  protected openAddRecurring(): void {
    this.openRecurring(null, {
      label: '',
      categoryId: null,
      amount: 0,
      type: 'DEPENSE',
      variableAmount: false,
      transferAccountId: null,
      outgoing: true,
    });
  }

  protected editRecurring(recurring: RecurringTransaction): void {
    this.openRecurring(recurring.id, {
      label: recurring.label,
      categoryId: recurring.categoryId,
      amount: Math.abs(recurring.amount),
      type: recurring.type,
      variableAmount: recurring.variableAmount,
      transferAccountId: recurring.transferAccountId,
      outgoing: recurring.outgoing,
    });
  }

  private openRecurring(id: number | null, value: ReturnType<typeof this.recurringForm.getRawValue>): void {
    this.pendingDeleteRecurringId.set(null);
    this.recurringErrorMessage.set(null);
    this.editingRecurringId.set(id);
    // emitEvent: false - the category's valueChanges handler would replace the recurring's own type.
    this.recurringCategoryId.set(value.categoryId);
    this.recurringType.set(value.type);
    this.recurringForm.reset(value, { emitEvent: false });
    this.recurringDialog().nativeElement.showModal();
  }

  protected closeRecurring(): void {
    this.recurringDialog().nativeElement.close();
  }

  /** Closes on a backdrop click. Must return void: a handler evaluating to `false` makes Angular
   *  preventDefault() every click inside the dialog, which breaks its submit button. */
  protected onRecurringDialogClick(event: MouseEvent): void {
    if (event.target === this.recurringDialog().nativeElement) {
      this.closeRecurring();
    }
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
      variableAmount: raw.variableAmount,
      transferAccountId: raw.type === 'TRANSFERT' ? raw.transferAccountId : null,
      outgoing: raw.outgoing,
    };
    const id = this.editingRecurringId();
    const request$ = id ? this.recurringTransactionService.update(id, request) : this.recurringTransactionService.create(request);

    request$.subscribe({
      next: () => {
        this.closeRecurring();
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
