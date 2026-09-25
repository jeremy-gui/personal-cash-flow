import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, ElementRef, inject, signal, viewChild } from '@angular/core';
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
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly deleteError = signal<string | null>(null);
  private readonly formDialog = viewChild.required<ElementRef<HTMLDialogElement>>('formDialog');
  protected readonly accountTypes = Object.keys(ACCOUNT_TYPE_LABELS) as AccountType[];
  protected readonly typeLabels = ACCOUNT_TYPE_LABELS;

  protected readonly form = this.fb.nonNullable.group({
    name: ['', Validators.required],
    type: this.fb.nonNullable.control<AccountType>('COURANT'),
    initialBalance: [0, Validators.required],
    accountNumber: this.fb.control<string | null>(null),
    isDefault: this.fb.nonNullable.control(false),
  });

  constructor() {
    this.reload();
  }

  private reload(): void {
    this.accountService.list().subscribe((accounts) => this.accounts.set(accounts));
  }

  protected openAdd(): void {
    this.open(null, { name: '', type: 'COURANT', initialBalance: 0, accountNumber: null, isDefault: false });
  }

  protected edit(account: Account): void {
    this.open(account.id, {
      name: account.name,
      type: account.type,
      initialBalance: account.initialBalance,
      accountNumber: account.accountNumber,
      isDefault: account.isDefault,
    });
  }

  /** The form is (re)filled on every opening - never on the dialog's close event, unreliable after Escape. */
  private open(id: number | null, value: ReturnType<typeof this.form.getRawValue>): void {
    this.pendingDeleteId.set(null);
    this.errorMessage.set(null);
    this.editingId.set(id);
    this.form.reset(value);
    this.formDialog().nativeElement.showModal();
  }

  protected closeForm(): void {
    this.formDialog().nativeElement.close();
  }

  /** Closes on a backdrop click. Must return void: a handler evaluating to `false` makes Angular
   *  preventDefault() every click inside the dialog, which breaks its submit button. */
  protected onDialogClick(event: MouseEvent): void {
    if (event.target === this.formDialog().nativeElement) {
      this.closeForm();
    }
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

    request$.subscribe({
      next: () => {
        this.closeForm();
        this.reload();
      },
      error: (err: HttpErrorResponse) => this.errorMessage.set(err.error?.message ?? 'Une erreur est survenue.'),
    });
  }

  protected requestDelete(accountId: number): void {
    this.deleteError.set(null);
    this.pendingDeleteId.set(accountId);
  }

  protected cancelDelete(): void {
    this.pendingDeleteId.set(null);
  }

  protected confirmDelete(accountId: number): void {
    this.accountService.delete(accountId).subscribe({
      next: () => {
        this.pendingDeleteId.set(null);
        this.reload();
      },
      error: (err: HttpErrorResponse) => {
        this.pendingDeleteId.set(null);
        this.deleteError.set(err.error?.message ?? 'Suppression impossible.');
      },
    });
  }
}
