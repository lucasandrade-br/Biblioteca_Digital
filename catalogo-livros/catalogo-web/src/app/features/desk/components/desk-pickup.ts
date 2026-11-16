import { Component, DestroyRef, OnInit, inject, model, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { finalize, takeUntil } from 'rxjs';
import { apiErrorMessage } from '../../../core/http/api-error';
import { formatDate } from '../../../shared/format';
import { notBlank } from '../../../shared/forms/not-blank';
import { LOAN_STATUS_LABELS, Loan, LoanStatus } from '../../loans/models/loan.models';
import { DeskApi } from '../data-access/desk-api';

@Component({
  selector: 'app-desk-pickup',
  imports: [ReactiveFormsModule],
  template: `
    <div>
      <form class="inline-form" [formGroup]="pickupForm" (ngSubmit)="lookup()" novalidate>
        <div class="field">
          <label for="pickupCode">Código de retirada</label>
          <input id="pickupCode" formControlName="code" placeholder="LIV-XXXXXXXX" autocomplete="off"
            data-dialog-initial-focus (keydown.enter)="$event.preventDefault(); lookup()" />
        </div>
        <button class="button-secondary" type="submit" [disabled]="busy()">Consultar</button>
      </form>
      @if (pickupError()) {
        <div class="notice error" role="alert">{{ pickupError() }}</div>
      }
      @if (pickupPreview(); as preview) {
        @let loan = preview.loan;
        <div class="pickup-preview">
          <dl class="facts">
            <div><dt>Livro</dt><dd>{{ loan.bookTitle }}</dd></div>
            <div><dt>Leitor</dt><dd>{{ loan.readerName }} ({{ loan.readerEmail }})</dd></div>
            <div><dt>Situação</dt><dd>{{ label(loan.status) }}</dd></div>
            <div><dt>Retirar até</dt><dd>{{ loan.pickupDeadline ? date(loan.pickupDeadline) : "—" }}</dd></div>
          </dl>
          @if (loan.canPickup) {
            <button type="button" class="button-primary" [disabled]="busy()" (click)="confirmPickup()">Confirmar retirada</button>
          } @else {
            <div class="notice error" role="alert">Esta reserva não pode mais ser retirada.</div>
          }
        </div>
      }
    </div>
  `,
  styles: `
    :host { display: block; }
    .pickup-preview {
      border-top: 1px solid var(--line);
      padding-top: 6px;
    }
  `,
})
export class DeskPickup implements OnInit {
  private readonly api = inject(DeskApi);
  private readonly destroyRef = inject(DestroyRef);
  readonly busy = model(false);
  readonly pickupConfirmed = output<Loan>();
  protected readonly pickupForm = inject(FormBuilder).nonNullable.group({ code: ['', notBlank] });
  protected readonly pickupPreview = signal<{ code: string; loan: Loan } | null>(null);
  protected readonly pickupError = signal('');
  protected readonly date = formatDate;

  ngOnInit(): void {
    this.pickupForm.controls.code.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.pickupPreview.set(null);
      this.pickupError.set('');
    });
  }

  protected label(status: LoanStatus): string {
    return LOAN_STATUS_LABELS[status];
  }

  protected lookup(): void {
    if (this.busy()) return;
    this.pickupError.set('');
    this.pickupPreview.set(null);
    if (this.pickupForm.invalid) {
      this.pickupError.set('Informe o código de retirada.');
      return;
    }
    const code = this.pickupForm.controls.code.value.trim();
    this.busy.set(true);
    this.api.findByCode(code).pipe(
      takeUntil(this.pickupForm.controls.code.valueChanges),
      takeUntilDestroyed(this.destroyRef),
      finalize(() => this.busy.set(false)),
    ).subscribe({
      next: (loan) => this.pickupPreview.set({ code, loan }),
      error: (error: unknown) => {
        this.pickupError.set(apiErrorMessage(error, 'Não foi possível consultar o código.'));
      },
    });
  }

  protected confirmPickup(): void {
    const preview = this.pickupPreview();
    if (!preview || this.busy() || !preview.loan.canPickup) return;
    if (preview.code !== this.pickupForm.controls.code.value.trim()) return;

    this.busy.set(true);
    this.pickupError.set('');
    this.api.confirmPickup(preview.code).pipe(
      takeUntilDestroyed(this.destroyRef),
      finalize(() => this.busy.set(false)),
    ).subscribe({
      next: (loan) => {
        if (this.pickupPreview() === preview) this.pickupForm.reset();
        this.pickupConfirmed.emit(loan);
      },
      error: (error: unknown) => {
        this.pickupError.set(apiErrorMessage(error, 'Não foi possível registrar a retirada.'));
      },
    });
  }
}
