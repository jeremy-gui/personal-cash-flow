import { Routes } from '@angular/router';
import { AccountsComponent } from './features/accounts/accounts.component';
import { BudgetsComponent } from './features/budgets/budgets.component';
import { CategoriesComponent } from './features/categories/categories.component';
import { DashboardComponent } from './features/dashboard/dashboard.component';
import { TransactionsComponent } from './features/transactions/transactions.component';

export const routes: Routes = [
  { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
  { path: 'dashboard', component: DashboardComponent },
  { path: 'transactions', component: TransactionsComponent },
  { path: 'accounts', component: AccountsComponent },
  { path: 'categories', component: CategoriesComponent },
  { path: 'budgets', component: BudgetsComponent },
];
