import { CommonModule } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { Dashboard } from '../../core/models/dashboard.model';
import { DashboardService } from './dashboard.service';

function currentMonth(): string {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
}

@Component({
  selector: 'app-dashboard',
  imports: [CommonModule],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.css',
})
export class DashboardComponent {
  private readonly dashboardService = inject(DashboardService);

  protected readonly month = signal(currentMonth());
  protected readonly dashboard = signal<Dashboard | null>(null);

  protected readonly maxCategorySpend = computed(() => {
    const amounts = this.dashboard()?.depensesParCategorie.map((c) => c.amount) ?? [];
    return amounts.length ? Math.max(...amounts) : 0;
  });

  constructor() {
    this.reload();
  }

  private reload(): void {
    this.dashboardService.get(this.month()).subscribe((dashboard) => this.dashboard.set(dashboard));
  }

  protected onMonthChange(value: string): void {
    this.month.set(value);
    this.reload();
  }

  protected barWidth(amount: number): number {
    const max = this.maxCategorySpend();
    return max > 0 ? (amount / max) * 100 : 0;
  }
}
