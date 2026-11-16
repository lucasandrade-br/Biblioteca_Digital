import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { finalize } from 'rxjs';
import { ListFilters } from '../../../shared/components/list-filters';
import { ListPagination } from '../../../shared/components/list-pagination';
import { Modal } from '../../../shared/components/modal';
import { createPagedList } from '../../../shared/listing/paged-list';
import { apiErrorMessage } from '../../../core/http/api-error';
import { formatDate, formatDay, formatPrice } from '../../../shared/format';
import { LOAN_STATUS_LABELS, Loan, LoanStatus } from '../../loans/models/loan.models';
import { DeskPickup } from '../components/desk-pickup';
import { DeskAttendance } from '../components/desk-attendance';
import { Deadline, DeskApi, DeskSummary } from '../data-access/desk-api';

@Component({
  imports: [ReactiveFormsModule, ListFilters, ListPagination, Modal, DeskPickup, DeskAttendance],
  templateUrl: './desk.html',
})
export class Desk implements OnInit {
  private readonly api = inject(DeskApi);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly summary = signal<DeskSummary | null>(null);
  protected readonly filters = inject(FormBuilder).nonNullable.group({
    q: [''],
    status: [null as LoanStatus | null],
    pending: [true],
    deadline: ['TODOS' as Deadline],
    from: [''],
    to: [''],
  });
  protected readonly list = createPagedList(
    this.filters.getRawValue(),
    (filter, page, size) => this.api.list(filter, page, size),
    () => 'Não foi possível carregar a fila de atendimento.',
  );
  protected readonly datesError = signal('');
  protected readonly dialog = signal<'code' | 'attendance' | 'return' | null>(null);
  protected readonly selected = signal<Loan | null>(null);
  protected readonly busy = signal(false);
  protected readonly result = signal('');
  protected readonly returnError = signal('');
  protected readonly day = formatDay;
  protected readonly date = formatDate;
  protected readonly price = formatPrice;
  protected readonly labels = LOAN_STATUS_LABELS;
  protected readonly statuses = Object.keys(LOAN_STATUS_LABELS) as LoanStatus[];
  protected readonly deadlines: { value: Deadline; label: string }[] = [
    { value: 'ATRASO', label: 'Em atraso' },
    { value: 'HOJE', label: 'Hoje' },
    { value: 'PROXIMOS_7', label: 'Próximos 7 dias' },
  ];

  ngOnInit(): void {
    this.refresh();
  }

  protected apply(): void {
    const values = this.filters.getRawValue();
    if (values.from && values.to && values.from > values.to) {
      this.datesError.set('A data inicial deve ser anterior ou igual à final.');
      return;
    }
    this.datesError.set('');
    this.list.apply(values);
  }

  protected setDeadline(deadline: Deadline): void {
    this.filters.patchValue({ deadline, from: '', to: '' });
    this.apply();
  }

  protected selectStatus(): void {
    const status = this.filters.controls.status.value;
    if (status === 'RETURNED' || status === 'CANCELLED' || status === 'EXPIRED') {
      this.filters.controls.pending.setValue(false);
    }
  }

  protected clearFilters(): void {
    this.filters.reset();
    this.apply();
  }

  protected open(dialog: 'code' | 'attendance' | 'return', loan: Loan | null = null): void {
    this.selected.set(loan);
    this.returnError.set('');
    this.dialog.set(dialog);
  }

  protected confirmReturn(): void {
    const loan = this.selected();
    if (!loan || this.busy()) return;
    this.busy.set(true);
    this.returnError.set('');
    this.api.registerReturn(loan.id).pipe(
      takeUntilDestroyed(this.destroyRef), finalize(() => this.busy.set(false)),
    ).subscribe({
      next: (updated) => {
        this.dialog.set(null);
        const fine = updated.fineInCents > 0 ? ` Multa de ${formatPrice(updated.fineInCents)}.` : '';
        this.result.set(`Devolução de “${updated.bookTitle}” registrada em ${formatDate(updated.returnedAt!)}.${fine}`);
        this.refresh();
      },
      error: (error: unknown) => {
        this.returnError.set(apiErrorMessage(error, 'Não foi possível registrar a devolução.'));
      },
    });
  }

  protected confirmed(loan: Loan): void {
    this.dialog.set(null);
    this.result.set(`Empréstimo de “${loan.bookTitle}” registrado para ${loan.readerName}. Devolução até ${formatDay(loan.dueAt!)}.`);
    this.refresh();
  }

  private refresh(): void {
    this.api.summary().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (summary) => this.summary.set(summary),
      error: () => this.summary.set(null),
    });
    this.list.refresh();
  }
}
