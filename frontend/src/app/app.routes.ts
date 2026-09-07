import { Routes } from '@angular/router';
import { AccountsComponent } from './features/accounts/accounts.component';

export const routes: Routes = [
  { path: '', redirectTo: 'accounts', pathMatch: 'full' },
  { path: 'accounts', component: AccountsComponent },
];
