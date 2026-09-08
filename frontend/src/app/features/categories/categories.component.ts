import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import {
  Category,
  buildCategoryTree,
  excludeSubtree,
} from '../../core/models/category.model';
import { CategoryService } from './category.service';

@Component({
  selector: 'app-categories',
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './categories.component.html',
  styleUrl: './categories.component.css',
})
export class CategoriesComponent {
  private readonly categoryService = inject(CategoryService);
  private readonly fb = inject(FormBuilder);

  protected readonly categories = signal<Category[]>([]);
  protected readonly editingId = signal<number | null>(null);
  protected readonly errorMessage = signal<string | null>(null);

  protected readonly tree = computed(() => buildCategoryTree(this.categories()));
  protected readonly parentOptions = computed(() => {
    const id = this.editingId();
    const all = this.categories();
    return id === null ? all : excludeSubtree(all, id);
  });

  protected readonly form = this.fb.nonNullable.group({
    name: ['', Validators.required],
    parentId: this.fb.control<number | null>(null),
  });

  constructor() {
    this.reload();
  }

  private reload(): void {
    this.categoryService.list().subscribe((categories) => this.categories.set(categories));
  }

  protected edit(category: Category): void {
    this.editingId.set(category.id);
    this.errorMessage.set(null);
    this.form.setValue({ name: category.name, parentId: category.parentId });
  }

  protected cancelEdit(): void {
    this.editingId.set(null);
    this.form.reset({ name: '', parentId: null });
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
        this.cancelEdit();
        this.reload();
      },
      error: (err: HttpErrorResponse) => this.errorMessage.set(this.extractMessage(err)),
    });
  }

  protected remove(category: Category): void {
    if (!confirm(`Supprimer la catégorie "${category.name}" ?`)) {
      return;
    }
    this.errorMessage.set(null);
    this.categoryService.delete(category.id).subscribe({
      next: () => this.reload(),
      error: (err: HttpErrorResponse) => this.errorMessage.set(this.extractMessage(err)),
    });
  }

  private extractMessage(err: HttpErrorResponse): string {
    return err.error?.message ?? 'Une erreur est survenue.';
  }
}
