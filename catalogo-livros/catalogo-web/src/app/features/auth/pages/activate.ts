import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { apiErrorMessage } from '../../../core/http/api-error';
import { notBlank } from '../../../shared/forms/not-blank';
import { AuthLayout } from '../../../shared/layout/auth-layout';

@Component({
  imports: [ReactiveFormsModule, RouterLink, AuthLayout],
  template: `
    <app-auth-layout>
      <div class="form-content">
        <h2>Ativar conta</h2>
        <p>Use o código recebido no balcão para definir sua senha.</p>
        @if (error()) { <p class="notice error" role="alert">{{ error() }}</p> }
        <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
          <div class="field">
            <label for="activation-email">E-mail</label>
            <input id="activation-email" type="email" autocomplete="email" formControlName="email" maxlength="255" />
            @if (form.controls.email.touched && form.controls.email.invalid) {
              <small class="field-error">Informe um e-mail válido.</small>
            }
          </div>
          <div class="field">
            <label for="activation-code">Código de ativação</label>
            <input id="activation-code" autocomplete="off" spellcheck="false" autocapitalize="off"
              formControlName="code" maxlength="64" />
            @if (form.controls.code.touched && form.controls.code.invalid) {
              <small class="field-error">Informe o código fornecido no balcão.</small>
            }
          </div>
          <div class="field">
            <label for="activation-password">Nova senha</label>
            <input id="activation-password" type="password" autocomplete="new-password" formControlName="password" maxlength="72" />
            @if (form.controls.password.touched && form.controls.password.invalid) {
              <small class="field-error">Use pelo menos 6 caracteres.</small>
            }
          </div>
          <div class="field">
            <label for="activation-confirmation">Confirme a senha</label>
            <input id="activation-confirmation" type="password" autocomplete="new-password" formControlName="confirmation" maxlength="72" />
          </div>
          <button type="submit" class="button-primary" [disabled]="pending()">Ativar conta</button>
        </form>
        <p class="form-switch"><a routerLink="/entrar">Voltar ao login</a></p>
      </div>
    </app-auth-layout>
  `,
})
export class Activate {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly error = signal('');
  protected readonly pending = signal(false);
  protected readonly form = inject(FormBuilder).nonNullable.group({
    email: ['', [notBlank, Validators.email, Validators.maxLength(255)]],
    code: ['', [notBlank, Validators.maxLength(64)]],
    password: ['', [notBlank, Validators.minLength(6), Validators.maxLength(72)]],
    confirmation: [''],
  });

  protected submit(): void {
    if (this.pending()) return;
    this.error.set('');
    this.form.markAllAsTouched();
    if (this.form.invalid) return;
    const { email, code, password, confirmation } = this.form.getRawValue();
    if (password !== confirmation) {
      this.error.set('As senhas precisam ser iguais.');
      return;
    }
    if (new TextEncoder().encode(password).length > 72) {
      this.error.set('A senha deve ter até 72 bytes em UTF-8.');
      return;
    }

    this.pending.set(true);
    this.auth.activate({ email: email.trim(), code: code.trim(), password }).pipe(
      takeUntilDestroyed(this.destroyRef), finalize(() => this.pending.set(false)),
    ).subscribe({
      next: () => void this.router.navigate(['/entrar'], { queryParams: { ativacao: 'concluida' } }),
      error: (error: unknown) => this.error.set(apiErrorMessage(error, 'Não foi possível ativar a conta.')),
    });
  }
}
