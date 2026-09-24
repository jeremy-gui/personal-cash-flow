import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, ElementRef, inject, signal, viewChild } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Account } from '../../core/models/account.model';
import {
  Category,
  buildCategoryTree,
  excludeSubtree,
} from '../../core/models/category.model';
import { AccountService } from '../accounts/account.service';
import { CategoryService } from './category.service';

function hslToHex(h: number, s = 0.65, l = 0.55): string {
  const channel = (n: number) => {
    const k = (n + h / 30) % 12;
    const value = l - s * Math.min(l, 1 - l) * Math.max(-1, Math.min(k - 3, 9 - k, 1));
    return Math.round(value * 255).toString(16).padStart(2, '0');
  };
  return `#${channel(0)}${channel(8)}${channel(4)}`;
}

/** Same golden-angle spacing as the backend's CategoryColors, so the default suggested for the
 *  n-th root category is the color the server would have auto-assigned. */
const goldenColor = (index: number) => hslToHex((index * 137.508) % 360);

/** 12 evenly spaced hues, in rainbow order, for the swatch picker. */
const PRESET_COLORS = Array.from({ length: 12 }, (_, i) => hslToHex(i * 30));

@Component({
  selector: 'app-categories',
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './categories.component.html',
  styleUrl: './categories.component.css',
})
export class CategoriesComponent {
  private readonly categoryService = inject(CategoryService);
  private readonly accountService = inject(AccountService);
  private readonly fb = inject(FormBuilder);

  private readonly formDialog = viewChild.required<ElementRef<HTMLDialogElement>>('formDialog');

  protected readonly categories = signal<Category[]>([]);
  protected readonly accounts = signal<Account[]>([]);
  protected readonly editingId = signal<number | null>(null);
  protected readonly pendingDeleteId = signal<number | null>(null);
  protected readonly errorMessage = signal<string | null>(null);
  /** Shown right under the row whose deletion the server refused, so the reason stays in view. */
  protected readonly deleteError = signal<{ id: number; message: string } | null>(null);

  protected readonly tree = computed(() => buildCategoryTree(this.categories()));
  protected readonly parentOptions = computed(() => {
    const id = this.editingId();
    const all = this.categories();
    return buildCategoryTree(id === null ? all : excludeSubtree(all, id));
  });

  protected readonly form = this.fb.nonNullable.group({
    name: ['', Validators.required],
    parentId: this.fb.control<number | null>(null),
    defaultTransferAccountId: this.fb.control<number | null>(null),
    color: this.fb.control<string | null>(null),
  });

  /** Mirrors the form's parentId control as a signal so the template can reactively hide the
   *  color picker for subcategories (their color always follows their root, never editable). */
  protected readonly formParentId = signal<number | null>(null);
  protected readonly formColor = signal<string | null>(null);

  /** Preset swatches, plus the current color when it isn't one of them (e.g. picked as custom). */
  protected readonly swatches = computed(() => {
    const current = this.formColor();
    return current && !PRESET_COLORS.includes(current) ? [current, ...PRESET_COLORS] : PRESET_COLORS;
  });

  constructor() {
    this.reload();
    this.accountService.list().subscribe((accounts) => this.accounts.set(accounts));
    this.form.controls.parentId.valueChanges.subscribe((parentId) => this.formParentId.set(parentId));
    this.form.controls.color.valueChanges.subscribe((color) => this.formColor.set(color));
  }

  protected pickColor(color: string): void {
    this.form.controls.color.setValue(color);
  }

  private reload(): void {
    this.categoryService.list().subscribe((categories) => this.categories.set(categories));
  }

  protected openAdd(): void {
    this.pendingDeleteId.set(null);
    this.errorMessage.set(null);
    this.editingId.set(null);
    this.form.reset({
      name: '',
      parentId: null,
      defaultTransferAccountId: null,
      color: goldenColor(this.categories().filter((c) => c.parentId === null).length),
    });
    this.formDialog().nativeElement.showModal();
  }

  protected edit(category: Category): void {
    this.pendingDeleteId.set(null);
    this.editingId.set(category.id);
    this.errorMessage.set(null);
    this.form.setValue({
      name: category.name,
      parentId: category.parentId,
      defaultTransferAccountId: category.defaultTransferAccountId,
      color: category.color,
    });
    this.formDialog().nativeElement.showModal();
  }

  protected closeForm(): void {
    this.formDialog().nativeElement.close();
  }

  /** Closes on a backdrop click (the dialog element itself is the target). Must return void: a
   *  template handler evaluating to `false` makes Angular preventDefault() every click inside,
   *  which silently broke the submit button and the color input. */
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
    const value = this.form.getRawValue();
    const id = this.editingId();
    const request$ = id
      ? this.categoryService.update(id, value)
      : this.categoryService.create(value);

    request$.subscribe({
      next: () => {
        this.closeForm();
        this.reload();
      },
      error: (err: HttpErrorResponse) => this.errorMessage.set(this.extractMessage(err)),
    });
  }

  protected requestDelete(categoryId: number): void {
    this.editingId.set(null);
    this.deleteError.set(null);
    this.pendingDeleteId.set(categoryId);
  }

  protected cancelDelete(): void {
    this.pendingDeleteId.set(null);
  }

  protected confirmDelete(categoryId: number): void {
    this.errorMessage.set(null);
    this.categoryService.delete(categoryId).subscribe({
      next: () => {
        this.pendingDeleteId.set(null);
        this.reload();
      },
      error: (err: HttpErrorResponse) => {
        this.pendingDeleteId.set(null);
        this.deleteError.set({ id: categoryId, message: this.extractMessage(err) });
      },
    });
  }

  private extractMessage(err: HttpErrorResponse): string {
    return err.error?.message ?? 'Une erreur est survenue.';
  }
}
