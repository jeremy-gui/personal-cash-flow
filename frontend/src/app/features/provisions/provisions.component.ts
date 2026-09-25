import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, ElementRef, inject, signal, viewChild } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Account } from '../../core/models/account.model';
import { buildCategoryTree, Category } from '../../core/models/category.model';
import { Provision, ProvisionContribution, ProvisionContributionRequest, ProvisionRequest } from '../../core/models/provision.model';
import { AccountService } from '../accounts/account.service';
import { CategoryService } from '../categories/category.service';
import { ProvisionService } from './provision.service';

function today(): string {
  return new Date().toISOString().slice(0, 10);
}

@Component({
  selector: 'app-provisions',
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './provisions.component.html',
  styleUrl: './provisions.component.css',
})
export class ProvisionsComponent {
  private readonly provisionService = inject(ProvisionService);
  private readonly categoryService = inject(CategoryService);
  private readonly accountService = inject(AccountService);
  private readonly fb = inject(FormBuilder);

  protected readonly provisions = signal<Provision[]>([]);
  protected readonly categories = signal<Category[]>([]);
  protected readonly categoryTree = computed(() => buildCategoryTree(this.categories()));
  protected readonly accounts = signal<Account[]>([]);
  protected readonly editingId = signal<number | null>(null);
  protected readonly pendingDeleteId = signal<number | null>(null);
  protected readonly errorMessage = signal<string | null>(null);
  private readonly formDialog = viewChild.required<ElementRef<HTMLDialogElement>>('formDialog');

  protected readonly expandedId = signal<number | null>(null);
  protected readonly contributions = signal<ProvisionContribution[]>([]);
  protected readonly contributionsError = signal<string | null>(null);
  protected readonly pendingDeleteContributionId = signal<number | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    name: ['', Validators.required],
    targetAmount: [0, [Validators.required, Validators.min(0.01)]],
    targetDate: this.fb.control<string | null>(null),
    categoryId: this.fb.control<number | null>(null),
    accountId: this.fb.control<number | null>(null),
    active: [true],
  });

  protected readonly contributionForm = this.fb.nonNullable.group({
    amount: [0, [Validators.required, Validators.min(0.01)]],
    date: [today(), Validators.required],
  });

  constructor() {
    this.categoryService.list().subscribe((categories) => this.categories.set(categories));
    this.accountService.list().subscribe((accounts) => this.accounts.set(accounts));
    this.reload();
  }

  private reload(): void {
    this.provisionService.list().subscribe((provisions) => this.provisions.set(provisions));
  }

  protected openAdd(): void {
    this.open(null, { name: '', targetAmount: 0, targetDate: null, categoryId: null, accountId: null, active: true });
  }

  protected edit(provision: Provision): void {
    this.open(provision.id, {
      name: provision.name,
      targetAmount: provision.targetAmount,
      targetDate: provision.targetDate,
      categoryId: provision.categoryId,
      accountId: provision.accountId,
      active: provision.active,
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
    this.errorMessage.set(null);
    const request: ProvisionRequest = this.form.getRawValue();
    const id = this.editingId();
    const request$ = id ? this.provisionService.update(id, request) : this.provisionService.create(request);

    request$.subscribe({
      next: () => {
        this.closeForm();
        this.reload();
      },
      error: (err: HttpErrorResponse) => this.errorMessage.set(err.error?.message ?? 'Une erreur est survenue.'),
    });
  }

  protected requestDelete(provisionId: number): void {
    this.pendingDeleteId.set(provisionId);
  }

  protected cancelDelete(): void {
    this.pendingDeleteId.set(null);
  }

  protected confirmDelete(provisionId: number): void {
    this.provisionService.delete(provisionId).subscribe(() => {
      this.pendingDeleteId.set(null);
      if (this.expandedId() === provisionId) {
        this.expandedId.set(null);
      }
      this.reload();
    });
  }

  protected toggleExpanded(provisionId: number): void {
    if (this.expandedId() === provisionId) {
      this.expandedId.set(null);
      return;
    }
    this.expandedId.set(provisionId);
    this.contributionsError.set(null);
    this.pendingDeleteContributionId.set(null);
    this.contributionForm.reset({ amount: 0, date: today() });
    this.provisionService.listContributions(provisionId).subscribe((contributions) => this.contributions.set(contributions));
  }

  protected submitContribution(): void {
    const provisionId = this.expandedId();
    if (!provisionId || this.contributionForm.invalid) {
      return;
    }
    this.contributionsError.set(null);
    const request: ProvisionContributionRequest = this.contributionForm.getRawValue();

    this.provisionService.addContribution(provisionId, request).subscribe({
      next: () => {
        this.contributionForm.reset({ amount: 0, date: today() });
        this.provisionService.listContributions(provisionId).subscribe((contributions) => this.contributions.set(contributions));
        this.reload();
      },
      error: (err: HttpErrorResponse) => this.contributionsError.set(err.error?.message ?? 'Une erreur est survenue.'),
    });
  }

  protected requestDeleteContribution(contributionId: number): void {
    this.pendingDeleteContributionId.set(contributionId);
  }

  protected cancelDeleteContribution(): void {
    this.pendingDeleteContributionId.set(null);
  }

  protected confirmDeleteContribution(contributionId: number): void {
    const provisionId = this.expandedId();
    if (!provisionId) {
      return;
    }
    this.provisionService.deleteContribution(provisionId, contributionId).subscribe(() => {
      this.pendingDeleteContributionId.set(null);
      this.provisionService.listContributions(provisionId).subscribe((contributions) => this.contributions.set(contributions));
      this.reload();
    });
  }
}
