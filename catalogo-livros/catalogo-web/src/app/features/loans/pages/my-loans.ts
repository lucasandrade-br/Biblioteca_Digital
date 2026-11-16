import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Observable, finalize } from 'rxjs';
import { ListFilters } from '../../../shared/components/list-filters';
import { ListPagination } from '../../../shared/components/list-pagination';
import { Modal } from '../../../shared/components/modal';
import { createPagedList } from '../../../shared/listing/paged-list';
import { apiErrorMessage } from '../../../core/http/api-error';
import { formatDate, formatDay, formatPrice } from '../../../shared/format';
import { PickupQr } from '../components/pickup-qr';
import { LoanApi } from '../data-access/loan-api';
import { LOAN_STATUS_LABELS, Loan, LoanScope, LoanStatus } from '../models/loan.models';

const VIEWS: { key: string; scope: LoanScope; label: string; empty: string; statuses: LoanStatus[] }[] = [
  { key: 'reservas', scope: 'RESERVAS', label: 'Reservas', empty: 'Você não tem reservas aguardando retirada.', statuses: [] },
  { key: 'emprestimos', scope: 'EMPRESTIMOS', label: 'Empréstimos', empty: 'Você não tem empréstimos em aberto.', statuses: [] },
  {
    key: 'historico', scope: 'HISTORICO', label: 'Histórico', empty: 'Seu histórico ainda está vazio.',
    statuses: ['RETURNED', 'CANCELLED', 'EXPIRED'],
  },
];

@Component({
  imports: [ReactiveFormsModule, ListFilters, ListPagination, RouterLink, PickupQr, Modal],
  templateUrl: './my-loans.html',
})
export class MyLoans implements OnInit {
  private readonly api = inject(LoanApi);
  private readonly destroyRef = inject(DestroyRef);
  private readonly route = inject(ActivatedRoute);
  protected readonly views = VIEWS;
  protected readonly view = signal(VIEWS[1]);
  protected readonly filters = inject(FormBuilder).nonNullable.group({
    q: [''],
    status: [null as LoanStatus | null],
  });
  protected readonly list = createPagedList(
    { ...this.filters.getRawValue(), scope: this.view().scope },
    (filter, page, size) => this.api.listMine(filter, page, size),
    () => 'Não foi possível carregar seus empréstimos.',
  );
  protected readonly detail = signal<Loan | null>(null);
  protected readonly codeOpen = signal(false);
  protected readonly busy = signal(false);
  protected readonly message = signal<{ ok: boolean; text: string } | null>(null);
  protected readonly visibleMessage = computed(() => this.detail() ? null : this.message());
  protected readonly labels = LOAN_STATUS_LABELS;
  protected readonly date = formatDate;
  protected readonly day = formatDay;
  protected readonly price = formatPrice;

  ngOnInit(): void {
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      const view = VIEWS.find((option) => option.key === params.get('etapa')) ?? VIEWS[1];
      this.view.set(view);
      this.filters.controls.status.reset();
      this.apply();
    });
  }

  protected apply(): void {
    this.list.apply({ ...this.filters.getRawValue(), scope: this.view().scope });
  }

  protected clearFilters(): void {
    this.filters.reset();
    this.apply();
  }

  protected open(loan: Loan, code = false): void {
    this.message.set(null);
    this.codeOpen.set(code);
    this.detail.set(loan);
  }

  protected cancel(loan: Loan): void {
    this.run(this.api.cancel(loan.id), () => ({ ok: true, text: 'Reserva cancelada. O exemplar foi liberado.' }));
  }

  protected renew(loan: Loan): void {
    this.run(this.api.renew(loan.id), (updated) => ({
      ok: true,
      text: `Renovado! Nova data de devolução: ${formatDay(updated.dueAt!)}.`,
    }));
  }

  protected payFine(loan: Loan): void {
    this.run(this.api.payFine(loan.id), (updated) => updated.fineStatus === 'PAID'
      ? { ok: true, text: 'Multa quitada. Pagamento simulado; nenhum dinheiro foi movimentado.' }
      : { ok: false, text: 'Pagamento simulado recusado. Tente novamente.' });
  }

  private run(request: Observable<Loan>, result: (loan: Loan) => { ok: boolean; text: string }): void {
    if (this.busy()) return;
    this.busy.set(true);
    this.message.set(null);
    request.pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.busy.set(false))).subscribe({
      next: (updated) => {
        const previous = this.detail();
        this.detail.set(updated);
        this.list.items.update((loans) => loans.map((loan) => loan.id === updated.id ? updated : loan));
        this.message.set(result(updated));
        if (previous?.status !== updated.status || updated.fineStatus === 'PAID') this.list.refresh();
      },
      error: (error: unknown) => {
        this.message.set({ ok: false, text: apiErrorMessage(error, 'Não foi possível concluir a operação.') });
      },
    });
  }
}
