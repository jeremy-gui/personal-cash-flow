import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, ElementRef, inject, signal, viewChild } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { Observable } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { passwordsMatch } from '../../core/auth/passwords-match.validator';
import { AppUser, USER_ROLE_LABELS, UserRole } from '../../core/models/user.model';
import { UserService } from './user.service';

type DialogMode = 'create' | 'password' | 'username';

@Component({
  selector: 'app-users',
  imports: [DatePipe, ReactiveFormsModule],
  templateUrl: './users.component.html',
  styleUrl: './users.component.css',
})
export class UsersComponent {
  private readonly userService = inject(UserService);
  private readonly fb = inject(FormBuilder);
  private readonly router = inject(Router);
  protected readonly auth = inject(AuthService);

  protected readonly users = signal<AppUser[]>([]);
  protected readonly dialogMode = signal<DialogMode>('create');
  /** The user being edited (null when creating). */
  protected readonly targetUser = signal<AppUser | null>(null);
  protected readonly pendingDeleteId = signal<number | null>(null);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly deleteError = signal<string | null>(null);
  private readonly formDialog = viewChild.required<ElementRef<HTMLDialogElement>>('formDialog');
  protected readonly roles = Object.keys(USER_ROLE_LABELS) as UserRole[];
  protected readonly roleLabels = USER_ROLE_LABELS;

  protected readonly form = this.fb.nonNullable.group({
    username: ['', [Validators.required, Validators.maxLength(50)]],
    password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72)]],
    passwordConfirm: [''],
    role: this.fb.nonNullable.control<UserRole>('USER'),
  }, { validators: passwordsMatch('password', 'passwordConfirm') });

  constructor() {
    this.reload();
  }

  private reload(): void {
    this.userService.list().subscribe((users) => this.users.set(users));
  }

  protected openAdd(): void {
    this.open('create', null);
  }

  protected openReset(user: AppUser): void {
    this.open('password', user);
  }

  protected openRename(user: AppUser): void {
    this.open('username', user);
  }

  /** The form is (re)filled on every opening - never on the dialog's close event, unreliable after Escape.
   *  Fields the mode doesn't show are disabled, so their validators don't block the submit. */
  private open(mode: DialogMode, user: AppUser | null): void {
    this.pendingDeleteId.set(null);
    this.errorMessage.set(null);
    this.dialogMode.set(mode);
    this.targetUser.set(user);
    this.form.reset({ username: user?.username ?? '', password: '', passwordConfirm: '', role: user?.role ?? 'USER' });
    this.form.enable();
    const c = this.form.controls;
    if (mode === 'password') {
      c.username.disable();
      c.role.disable();
    } else if (mode === 'username') {
      c.password.disable();
      c.passwordConfirm.disable();
      c.role.disable();
    }
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
    const { passwordConfirm: _, ...value } = this.form.getRawValue();
    const user = this.targetUser();
    const mode = this.dialogMode();
    const request$: Observable<unknown> =
      mode === 'create' ? this.userService.create(value)
      : mode === 'password' ? this.userService.resetPassword(user!.id, value.password)
      : this.userService.rename(user!.id, value.username);

    request$.subscribe({
      next: () => {
        this.closeForm();
        // The session is tied to the old name: renaming yourself means logging in again.
        if (mode === 'username' && user!.username === this.auth.currentUser()?.username) {
          this.auth.logout().subscribe(() => this.router.navigate(['/login']));
          return;
        }
        this.reload();
      },
      error: (err: HttpErrorResponse) => this.errorMessage.set(err.error?.message ?? 'Une erreur est survenue.'),
    });
  }

  protected requestDelete(userId: number): void {
    this.deleteError.set(null);
    this.pendingDeleteId.set(userId);
  }

  protected cancelDelete(): void {
    this.pendingDeleteId.set(null);
  }

  protected confirmDelete(userId: number): void {
    this.userService.delete(userId).subscribe({
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
