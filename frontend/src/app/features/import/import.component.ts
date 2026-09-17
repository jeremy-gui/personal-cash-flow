import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { Account } from '../../core/models/account.model';
import { ImportSummary } from '../../core/models/import.model';
import { AccountService } from '../accounts/account.service';
import { ImportService } from './import.service';

@Component({
  selector: 'app-import',
  imports: [CommonModule],
  templateUrl: './import.component.html',
  styleUrl: './import.component.css',
})
export class ImportComponent {
  private readonly importService = inject(ImportService);
  private readonly accountService = inject(AccountService);

  protected readonly accounts = signal<Account[]>([]);
  protected readonly selectedFile = signal<File | null>(null);
  protected readonly history = signal<ImportSummary[]>([]);
  protected readonly lastResult = signal<ImportSummary | null>(null);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly uploading = signal(false);

  constructor() {
    this.accountService.list().subscribe((accounts) => this.accounts.set(accounts));
    this.reloadHistory();
  }

  private reloadHistory(): void {
    this.importService.history().subscribe((history) => this.history.set(history));
  }

  protected onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.selectedFile.set(input.files?.[0] ?? null);
  }

  protected upload(): void {
    const file = this.selectedFile();
    if (file === null) {
      return;
    }
    this.errorMessage.set(null);
    this.lastResult.set(null);
    this.uploading.set(true);

    this.importService.upload(file).subscribe({
      next: (result) => {
        this.lastResult.set(result);
        this.uploading.set(false);
        this.selectedFile.set(null);
        this.reloadHistory();
      },
      error: (err: HttpErrorResponse) => {
        this.errorMessage.set(err.error?.message ?? 'Une erreur est survenue.');
        this.uploading.set(false);
      },
    });
  }

  protected accountName(accountId: number): string {
    return this.accounts().find((a) => a.id === accountId)?.name ?? `#${accountId}`;
  }
}
