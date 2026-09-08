import { Routes } from '@angular/router';
import { AccountsComponent } from './features/accounts/accounts.component';
import { CategoriesComponent } from './features/categories/categories.component';

export const routes: Routes = [
  { path: '', redirectTo: 'accounts', pathMatch: 'full' },
  { path: 'accounts', component: AccountsComponent },
  { path: 'categories', component: CategoriesComponent },
];
