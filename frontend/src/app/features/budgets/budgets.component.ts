import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Budget, BudgetRequest } from '../../core/models/budget.model';
import { Category } from '../../core/models/category.model';
import { CategoryService } from '../categories/category.service';
import { BudgetService } from './budget.service';

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
  private readonly fb = inject(FormBuilder);

  protected readonly month = signal(currentMonth());
  protected readonly budgets = signal<Budget[]>([]);
  protected readonly categories = signal<Category[]>([]);
  protected readonly editingId = signal<number | null>(null);
  protected readonly errorMessage = signal<string | null>(null);

  protected readonly availableCategories = computed(() => {
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

  constructor() {
    this.categoryService.list().subscribe((categories) => this.categories.set(categories));
    this.reload();
  }

  private reload(): void {
    this.budgetService.listForMonth(this.month()).subscribe((budgets) => this.budgets.set(budgets));
  }

  protected onMonthChange(value: string): void {
    this.month.set(value);
    this.cancelEdit();
    this.reload();
  }

  protected edit(budget: Budget): void {
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

  protected remove(budget: Budget): void {
    if (!confirm(`Supprimer le budget "${budget.categoryName}" ?`)) {
      return;
    }
    this.budgetService.delete(budget.id).subscribe(() => this.reload());
  }
}
