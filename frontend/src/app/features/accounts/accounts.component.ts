import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import {
  ACCOUNT_TYPE_LABELS,
  Account,
  AccountType,
} from '../../core/models/account.model';
import { AccountService } from './account.service';

@Component({
  selector: 'app-accounts',
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './accounts.component.html',
  styleUrl: './accounts.component.css',
})
export class AccountsComponent {
  private readonly accountService = inject(AccountService);
  private readonly fb = inject(FormBuilder);

  protected readonly accounts = signal<Account[]>([]);
  protected readonly editingId = signal<number | null>(null);
  protected readonly pendingDeleteId = signal<number | null>(null);
  protected readonly accountTypes = Object.keys(ACCOUNT_TYPE_LABELS) as AccountType[];
  protected readonly typeLabels = ACCOUNT_TYPE_LABELS;

  protected readonly form = this.fb.nonNullable.group({
    name: ['', Validators.required],
    type: this.fb.nonNullable.control<AccountType>('COURANT'),
    initialBalance: [0, Validators.required],
  });

  constructor() {
    this.reload();
  }

  private reload(): void {
    this.accountService.list().subscribe((accounts) => this.accounts.set(accounts));
  }

  protected edit(account: Account): void {
    this.pendingDeleteId.set(null);
    this.editingId.set(account.id);
    this.form.setValue({
      name: account.name,
      type: account.type,
      initialBalance: account.initialBalance,
    });
  }

  protected cancelEdit(): void {
    this.editingId.set(null);
    this.form.reset({ name: '', type: 'COURANT', initialBalance: 0 });
  }

  protected submit(): void {
    if (this.form.invalid) {
      return;
    }
    const value = this.form.getRawValue();
    const id = this.editingId();
    const request$ = id
      ? this.accountService.update(id, value)
      : this.accountService.create(value);

    request$.subscribe(() => {
      this.cancelEdit();
      this.reload();
    });
  }

  protected requestDelete(accountId: number): void {
    this.editingId.set(null);
    this.pendingDeleteId.set(accountId);
  }

  protected cancelDelete(): void {
    this.pendingDeleteId.set(null);
  }

  protected confirmDelete(accountId: number): void {
    this.accountService.delete(accountId).subscribe(() => {
      this.pendingDeleteId.set(null);
      this.reload();
    });
  }
}
