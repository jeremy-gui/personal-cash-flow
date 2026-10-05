import { HttpErrorResponse } from '@angular/common/http';
import { Component, ElementRef, inject, signal, viewChild } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from './core/auth/auth.service';
import { passwordsMatch } from './core/auth/passwords-match.validator';

@Component({
  imports: [ReactiveFormsModule, RouterOutlet, RouterLink, RouterLinkActive],
  selector: 'app-root',
  styleUrl: './app.css',
  templateUrl: './app.html',
})
export class App {
  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);

  protected readonly passwordError = signal<string | null>(null);
  private readonly passwordDialog = viewChild.required<ElementRef<HTMLDialogElement>>('passwordDialog');

  protected readonly passwordForm = this.fb.nonNullable.group({
    currentPassword: ['', Validators.required],
    newPassword: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72)]],
    newPasswordConfirm: [''],
  }, { validators: passwordsMatch('newPassword', 'newPasswordConfirm') });

  protected logout(): void {
    this.auth.logout().subscribe(() => this.router.navigate(['/login']));
  }

  protected openPasswordDialog(): void {
    this.passwordError.set(null);
    this.passwordForm.reset();
    this.passwordDialog().nativeElement.showModal();
  }

  protected closePasswordDialog(): void {
    this.passwordDialog().nativeElement.close();
  }

  /** Closes on a backdrop click; must return void (see AccountsComponent.onDialogClick). */
  protected onPasswordDialogClick(event: MouseEvent): void {
    if (event.target === this.passwordDialog().nativeElement) {
      this.closePasswordDialog();
    }
  }

  protected submitPassword(): void {
    if (this.passwordForm.invalid) {
      return;
    }
    const { currentPassword, newPassword } = this.passwordForm.getRawValue();
    this.auth.changePassword(currentPassword, newPassword).subscribe({
      next: () => this.closePasswordDialog(),
      error: (err: HttpErrorResponse) => this.passwordError.set(err.error?.message ?? 'Une erreur est survenue.'),
    });
  }
}
