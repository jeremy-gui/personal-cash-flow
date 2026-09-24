import { CommonModule, formatDate } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { Category, categoryColor } from '../../core/models/category.model';
import { Dashboard } from '../../core/models/dashboard.model';
import { Transaction } from '../../core/models/transaction.model';
import { AccountService } from '../accounts/account.service';
import { CategoryService } from '../categories/category.service';
import { TransactionService } from '../transactions/transaction.service';
import { DashboardService } from './dashboard.service';

function currentMonth(): string {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
}

const DONUT_PALETTE = ['#2563eb', '#7c3aed', '#db2777', '#ea580c', '#0891b2', '#65a30d', '#ca8a04', '#64748b'];

interface DonutSegment {
  categoryName: string;
  amount: number;
  start: number;
  end: number;
  color: string;
}

@Component({
  selector: 'app-dashboard',
  imports: [CommonModule],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.css',
})
export class DashboardComponent {
  private readonly dashboardService = inject(DashboardService);
  private readonly transactionService = inject(TransactionService);
  private readonly accountService = inject(AccountService);
  private readonly categoryService = inject(CategoryService);

  protected readonly categories = signal<Category[]>([]);

  protected readonly month = signal(currentMonth());
  protected readonly monthLabel = computed(() => {
    const [year, monthNum] = this.month().split('-').map(Number);
    return formatDate(new Date(year, monthNum - 1, 1), 'MMMM yyyy', 'fr-FR');
  });
  /** "de " or "d'" - avril/août/octobre need the elision, the rest don't. */
  protected readonly monthLabelPrefix = computed(() => (/^[aeiou]/i.test(this.monthLabel()) ? "d'" : 'de '));
  protected readonly dashboard = signal<Dashboard | null>(null);
  protected readonly dashboardError = signal<string | null>(null);
  protected readonly recentTransactions = signal<Transaction[]>([]);
  private defaultAccountId: number | null = null;

  protected readonly categoryDonutSegments = computed<DonutSegment[]>(() => {
    const spending = this.dashboard()?.depensesParCategorie ?? [];
    const total = spending.reduce((sum, c) => sum + c.amount, 0);
    if (total <= 0) {
      return [];
    }
    let cumulative = 0;
    return spending.map((category, index) => {
      const share = (category.amount / total) * 100;
      const start = cumulative;
      cumulative += share;
      return {
        categoryName: category.categoryName,
        amount: category.amount,
        start,
        end: cumulative,
        color: DONUT_PALETTE[index % DONUT_PALETTE.length],
      };
    });
  });

  /** The donut's own total: `depenses` also counts outgoing transfers, which the donut doesn't show. */
  protected readonly categoryDonutTotal = computed(() =>
    this.categoryDonutSegments().reduce((sum, s) => sum + s.amount, 0),
  );

  protected readonly categoryDonutGradient = computed(() => {
    const segments = this.categoryDonutSegments();
    if (!segments.length) {
      return 'conic-gradient(#e6e9ee 0deg 360deg)';
    }
    const stops = segments.map((s) => `${s.color} ${s.start * 3.6}deg ${s.end * 3.6}deg`).join(', ');
    return `conic-gradient(${stops})`;
  });

  /** Keeps "Budgets par catégorie" in the same colors as the "Dépenses par catégorie" donut. */
  private readonly categoryColorMap = computed(() => {
    const map = new Map<string, string>();
    this.categoryDonutSegments().forEach((s) => map.set(s.categoryName, s.color));
    return map;
  });

  constructor() {
    this.categoryService.list().subscribe((categories) => this.categories.set(categories));
    this.accountService.list().subscribe((accounts) => {
      this.defaultAccountId = accounts.find((a) => a.isDefault)?.id ?? null;
      this.reload();
    });
  }

  private reload(): void {
    this.dashboardService.get(this.month()).subscribe({
      next: (dashboard) => {
        this.dashboard.set(dashboard);
        this.dashboardError.set(null);
      },
      error: (err: HttpErrorResponse) => {
        this.dashboardError.set(err.error?.message ?? 'Une erreur est survenue.');
      },
    });
    this.transactionService
      .search({ month: this.month(), accountId: this.defaultAccountId, categoryId: null, status: null, type: null })
      .subscribe((transactions) => this.recentTransactions.set(transactions.slice(0, 5)));
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

  protected categoryColorFor(categoryId: number | null): string {
    return categoryColor(this.categories(), categoryId);
  }

  protected categoryInitial(transaction: Transaction): string {
    return (transaction.categoryName ?? transaction.label).trim().charAt(0).toUpperCase();
  }

  protected categoryColor(transaction: Transaction): string {
    return this.hashColor(transaction.categoryName ?? transaction.label);
  }

  /** A budget's category may have no spending yet, so it won't be in the donut - fall back to
   *  the same hash-based color used for uncategorized transactions rather than leaving it blank. */
  protected budgetColor(categoryName: string): string {
    return this.categoryColorMap().get(categoryName) ?? this.hashColor(categoryName);
  }

  private hashColor(key: string): string {
    let hash = 0;
    for (let i = 0; i < key.length; i++) {
      hash = (hash * 31 + key.charCodeAt(i)) >>> 0;
    }
    return DONUT_PALETTE[hash % DONUT_PALETTE.length];
  }
}
