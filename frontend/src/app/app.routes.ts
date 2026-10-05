import { Routes } from '@angular/router';
import { adminGuard, authGuard } from './core/auth/auth.guard';
import { AccountsComponent } from './features/accounts/accounts.component';
import { AnalysisComponent } from './features/analysis/analysis.component';
import { BudgetsComponent } from './features/budgets/budgets.component';
import { CategoriesComponent } from './features/categories/categories.component';
import { DashboardComponent } from './features/dashboard/dashboard.component';
import { ImportComponent } from './features/import/import.component';
import { LoginComponent } from './features/login/login.component';
import { ProvisionsComponent } from './features/provisions/provisions.component';
import { RepartitionComponent } from './features/repartition/repartition.component';
import { TransactionsComponent } from './features/transactions/transactions.component';
import { UsersComponent } from './features/users/users.component';

export const routes: Routes = [
  { path: 'login', component: LoginComponent },
  {
    path: '',
    canActivate: [authGuard],
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      { path: 'dashboard', component: DashboardComponent },
      { path: 'transactions', component: TransactionsComponent },
      { path: 'accounts', component: AccountsComponent },
      { path: 'categories', component: CategoriesComponent },
      { path: 'budgets', component: BudgetsComponent },
      { path: 'provisions', component: ProvisionsComponent },
      { path: 'repartition', component: RepartitionComponent },
      { path: 'analysis', component: AnalysisComponent },
      { path: 'import', component: ImportComponent },
      { path: 'users', component: UsersComponent, canActivate: [adminGuard] },
    ],
  },
];
