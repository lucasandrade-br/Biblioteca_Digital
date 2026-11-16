import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { EMPTY, catchError, distinctUntilChanged, map, switchMap } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { apiErrorMessage } from '../../../core/http/api-error';
import { BookCover } from '../../../shared/components/book-cover';
import { formatDate } from '../../../shared/format';
import { LoanApi } from '../../loans/data-access/loan-api';
import { PickupQr } from '../../loans/components/pickup-qr';
import { Loan } from '../../loans/models/loan.models';
import { BookApi } from '../data-access/book-api';
import { Book } from '../models/book.models';

@Component({
  imports: [RouterLink, BookCover, PickupQr],
  template: `
    <section class="page">
      <a class="text-link" routerLink="/livros">← Voltar ao catálogo</a>
      @if (loading()) {
        <p class="page-message" role="status">Carregando livro...</p>
      } @else if (error()) {
        <div class="notice error" role="alert">{{ error() }}</div>
      } @else if (book(); as data) {
        <div class="book-detail">
          <app-book-cover [title]="data.titulo" [author]="authorNames(data)" [url]="data.coverUrl" />
          <div class="book-detail-copy">
            <span class="section-kicker">{{ data.genre.name }}</span>
            <h1>{{ data.titulo }}</h1>
            <p class="page-intro">{{ authorNames(data) }}</p>
            <p>{{ data.synopsis }}</p>
            <dl class="facts">
              <div><dt>Editora</dt><dd>{{ data.publisher }}</dd></div>
              <div><dt>Ano</dt><dd>{{ data.publicationYear }}</dd></div>
              <div><dt>Páginas</dt><dd>{{ data.numeroPaginas }}</dd></div>
              <div><dt>ISBN</dt><dd>{{ data.isbn }}</dd></div>
              <div><dt>Exemplares</dt><dd>{{ data.availableCopies }} de {{ data.totalCopies }} disponíveis</dd></div>
            </dl>

            @if (loan(); as reserved) {
              @if (reserved.canPickup && reserved.pickupCode && reserved.pickupDeadline) {
                <section class="panel pickup-panel" aria-live="polite">
                  <div class="notice success" role="status">Reserva confirmada! Retire o livro no balcão.</div>
                  <app-pickup-qr [value]="reserved.pickupCode" [label]="reserved.bookTitle" />
                  <p><strong>Código:</strong> {{ reserved.pickupCode }}</p>
                  <p>Retire até {{ format(reserved.pickupDeadline) }}. Depois disso o exemplar é liberado.</p>
                  <a class="button-primary" routerLink="/emprestimos" [queryParams]="{ etapa: 'reservas' }">Ver minha reserva</a>
                </section>
              } @else {
                <a class="button-primary" routerLink="/emprestimos">Ver meus empréstimos</a>
              }
            } @else if (!auth.session()) {
              <a class="button-primary" routerLink="/entrar">Entre para reservar</a>
            } @else {
              @if (data.availableCopies === 0) {
                <div class="notice" role="status">Nenhum exemplar disponível no catálogo. Ao tentar reservar, o sistema verifica se houve liberação.</div>
              }
              @if (reserveError()) {
                <div class="notice error" role="alert">{{ reserveError() }}</div>
              }
              <button type="button" class="button-primary" [disabled]="reserving()" (click)="reserve(data.id)">
                {{ reserving() ? 'Reservando…' : 'Reservar exemplar' }}
              </button>
              <p class="hint">Confira o prazo de retirada após reservar o exemplar.</p>
            }
          </div>
        </div>
      }
    </section>
  `,
})
export class BookDetail implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly bookApi = inject(BookApi);
  private readonly loanApi = inject(LoanApi);
  private readonly destroyRef = inject(DestroyRef);
  private idempotencyKey = '';
  protected readonly auth = inject(AuthService);
  protected readonly book = signal<Book | null>(null);
  protected readonly loan = signal<Loan | null>(null);
  protected readonly loading = signal(true);
  protected readonly reserving = signal(false);
  protected readonly error = signal('');
  protected readonly reserveError = signal('');
  protected readonly format = formatDate;

  ngOnInit(): void {
    this.route.paramMap.pipe(
      map((params) => Number(params.get('id'))),
      distinctUntilChanged(),
      switchMap((bookId) => {
        this.idempotencyKey = crypto.randomUUID();
        this.book.set(null);
        this.loan.set(null);
        this.loading.set(true);
        this.reserving.set(false);
        this.error.set('');
        this.reserveError.set('');
        return this.bookApi.get(bookId).pipe(
          catchError((error: unknown) => {
            this.error.set(apiErrorMessage(error, 'Não foi possível carregar o livro.'));
            this.loading.set(false);
            return EMPTY;
          }),
        );
      }),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe((book) => {
      this.book.set(book);
      this.loading.set(false);
    });
  }

  protected authorNames(book: Book): string {
    return book.authors.map((author) => author.name).join(', ');
  }

  protected reserve(bookId: number): void {
    if (this.book()?.id !== bookId || this.reserving() || this.loan()) return;
    const key = this.idempotencyKey;
    this.reserving.set(true);
    this.reserveError.set('');
    this.loanApi.reserve(bookId, key).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (loan) => {
        if (this.idempotencyKey !== key) return;
        this.loan.set(loan);
        this.book.update((book) => book && { ...book, availableCopies: Math.max(0, book.availableCopies - 1) });
        this.reserving.set(false);
      },
      error: (error: unknown) => {
        if (this.idempotencyKey !== key) return;
        this.reserveError.set(apiErrorMessage(error, 'Não foi possível reservar o livro. Tente novamente.'));
        this.reserving.set(false);
      },
    });
  }
}
