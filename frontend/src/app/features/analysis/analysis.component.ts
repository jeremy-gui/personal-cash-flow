import { CommonModule, formatDate } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { ACCOUNT_TYPE_LABELS, Account, AccountType } from '../../core/models/account.model';
import { YearlyAnalysis } from '../../core/models/analysis.model';
import { Category, categoryColor } from '../../core/models/category.model';
import { Provision } from '../../core/models/provision.model';
import { AccountService } from '../accounts/account.service';
import { CategoryService } from '../categories/category.service';
import { ProvisionService } from '../provisions/provision.service';
import { AnalysisService } from './analysis.service';

const DONUT_PALETTE = ['#2563eb', '#7c3aed', '#db2777', '#ea580c', '#0891b2', '#65a30d', '#ca8a04', '#64748b'];

interface DonutSegment {
  categoryName: string;
  amount: number;
  start: number;
  end: number;
  color: string;
}

interface PatrimoineGroup {
  type: AccountType;
  label: string;
  total: number;
}

@Component({
  selector: 'app-analysis',
  imports: [CommonModule],
  templateUrl: './analysis.component.html',
  styleUrl: './analysis.component.css',
})
export class AnalysisComponent {
  private readonly analysisService = inject(AnalysisService);
  private readonly accountService = inject(AccountService);
  private readonly provisionService = inject(ProvisionService);
  private readonly categoryService = inject(CategoryService);

  protected readonly year = signal(new Date().getFullYear());
  protected readonly analysis = signal<YearlyAnalysis | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly accounts = signal<Account[]>([]);
  protected readonly provisions = signal<Provision[]>([]);
  protected readonly categories = signal<Category[]>([]);

  protected readonly monthlyResultsWithLabel = computed(() =>
    (this.analysis()?.monthlyResults ?? []).map((result) => ({
      ...result,
      label: formatDate(new Date(this.year(), result.month - 1, 1), 'MMM', 'fr-FR'),
    })),
  );

  protected readonly categoryDonutSegments = computed<DonutSegment[]>(() => {
    const categories = this.analysis()?.categoryBreakdown ?? [];
    const total = categories.reduce((sum, c) => sum + c.amount, 0);
    if (total <= 0) {
      return [];
    }
    let cumulative = 0;
    return categories.map((category, index) => {
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

  protected readonly categoryDonutGradient = computed(() => {
    const segments = this.categoryDonutSegments();
    if (!segments.length) {
      return 'conic-gradient(#e6e9ee 0deg 360deg)';
    }
    const stops = segments.map((s) => `${s.color} ${s.start * 3.6}deg ${s.end * 3.6}deg`).join(', ');
    return `conic-gradient(${stops})`;
  });

  protected readonly patrimoineByType = computed<PatrimoineGroup[]>(() => {
    const groups = new Map<AccountType, number>();
    for (const account of this.accounts()) {
      groups.set(account.type, (groups.get(account.type) ?? 0) + account.balance);
    }
    return Array.from(groups.entries()).map(([type, total]) => ({ type, label: ACCOUNT_TYPE_LABELS[type], total }));
  });

  protected readonly patrimoineTotal = computed(() =>
    this.accounts().reduce((sum, account) => sum + account.balance, 0),
  );

  protected readonly provisionsActiveTotal = computed(() =>
    this.provisions()
      .filter((p) => p.active)
      .reduce((sum, p) => sum + p.currentAmount, 0),
  );

  protected readonly argentDisponible = computed(() => this.patrimoineTotal() - this.provisionsActiveTotal());

  constructor() {
    this.accountService.list().subscribe((accounts) => this.accounts.set(accounts));
    this.provisionService.list().subscribe((provisions) => this.provisions.set(provisions));
    this.categoryService.list().subscribe((categories) => this.categories.set(categories));
    this.reload();
  }

  protected categoryColor(categoryId: number): string {
    return categoryColor(this.categories(), categoryId);
  }

  private reload(): void {
    this.analysisService.yearly(this.year()).subscribe({
      next: (analysis) => {
        this.analysis.set(analysis);
        this.error.set(null);
      },
      error: (err: HttpErrorResponse) => this.error.set(err.error?.message ?? 'Une erreur est survenue.'),
    });
  }

  protected onYearChange(delta: number): void {
    this.year.set(this.year() + delta);
    this.reload();
  }
}
