import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, TranslatePipe],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
})
export class LoginComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly form = this.fb.nonNullable.group({
    username: ['demo', Validators.required],
    password: ['demo', Validators.required],
    role: ['ROLE_USER', Validators.required],
  });

  readonly submitting = signal(false);
  readonly redirecting = signal(false);
  readonly error = signal<string | null>(null);

  submit(): void {
    if (this.form.invalid || this.submitting()) {
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    this.auth.demoLogin(this.form.getRawValue()).subscribe({
      next: () => {
        // Show a brief "Redirecting…" state before sending the user back to wherever they were
        // headed before the guard bounced them here (returnUrl), defaulting to the chat page.
        this.redirecting.set(true);
        setTimeout(() => void this.router.navigateByUrl(this.returnUrl()), 700);
      },
      error: () => {
        this.error.set('login.failed');
        this.submitting.set(false);
      },
    });
  }

  loginWithGoogle(): void {
    this.auth.loginWithGoogle();
  }

  private returnUrl(): string {
    return this.route.snapshot.queryParamMap.get('returnUrl') ?? '/chat';
  }
}
