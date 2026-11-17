import { Component, DestroyRef, OnInit, afterNextRender, inject, input, model, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { Observable, finalize } from 'rxjs';
import { apiErrorMessage } from '../../../core/http/api-error';
import { ListPagination } from '../../../shared/components/list-pagination';
import { formatDate } from '../../../shared/format';
import { createPagedList } from '../../../shared/listing/paged-list';
import { BookApi } from '../../books/data-access/book-api';
import { Loan } from '../../loans/models/loan.models';
import { DeskApi, DeskReader, Preparation } from '../data-access/desk-api';

@Component({
  selector: 'app-desk-attendance',
  imports: [ReactiveFormsModule, ListPagination],
  templateUrl: './desk-attendance.html',
})
export class DeskAttendance implements OnInit {
  private readonly api = inject(DeskApi);
  private readonly bookApi = inject(BookApi);
  private readonly destroyRef = inject(DestroyRef);
  private readonly forms = inject(FormBuilder).nonNullable;
  readonly initialReservation = input<Loan | null>(null);
  readonly busy = model(false);
  readonly completed = output<Loan>();
  protected readonly readers = createPagedList(
    '', (term, page, size) => this.api.searchReaders(term, page, size),
    () => 'Não foi possível buscar os leitores.', 12,
  );
  protected readonly books = createPagedList(
    '', (q, page, size) => this.bookApi.list({ q }, page, size),
    () => 'Não foi possível buscar os livros.', 12,
  );
  protected readonly readerQuery = this.forms.control('');
  protected readonly bookQuery = this.forms.control('');
  protected readonly readerSearched = signal(false);
  protected readonly bookSearched = signal(false);
  protected readonly reader = signal<DeskReader | null>(null);
  protected readonly book = signal<{ id: number; title: string } | null>(null);
  protected readonly preparation = signal<Preparation | null>(null);
  protected readonly identityChecked = signal(false);
  protected readonly error = signal('');
  protected readonly date = formatDate;
  private key: string | null = null;

  constructor() {
    afterNextRender(() => {
      if (this.initialReservation()) this.prepare();
    });
  }

  ngOnInit(): void {
    const loan = this.initialReservation();
    if (!loan) return;
    this.reader.set({
      id: loan.readerId, nome: loan.readerName, email: loan.readerEmail, ativo: true,
    });
    this.book.set({ id: loan.bookId, title: loan.bookTitle });
  }

  protected searchReaders(): void {
    const q = this.readerQuery.value.trim();
    this.readerSearched.set(!!q);
    if (q) this.readers.apply(q);
  }

  protected searchBooks(): void {
    const q = this.bookQuery.value.trim();
    this.bookSearched.set(!!q);
    if (q) this.books.apply(q);
  }

  protected chooseReader(reader: DeskReader | null): void {
    this.reader.set(reader);
    this.book.set(null);
    this.bookQuery.reset();
    this.bookSearched.set(false);
    this.resetPreparation();
  }

  protected chooseBook(book: { id: number; title: string } | null): void {
    this.book.set(book);
    this.resetPreparation();
    if (book) this.prepare();
  }

  protected prepare(): void {
    const reader = this.reader();
    const book = this.book();
    if (!reader || !book) return;
    this.run(this.api.prepare(reader.id, book.id), (result) => this.preparation.set(result),
      'Não foi possível consultar o atendimento.');
  }

  protected confirm(): void {
    const reader = this.reader();
    const book = this.book();
    const preparation = this.preparation();
    if (!reader || !book || !preparation || !this.identityChecked() || this.busy()) return;
    const reservation = preparation.reservation;
    if (reservation) {
      this.run(this.api.confirmAssistedPickup(reservation.id), (loan) => this.completed.emit(loan),
        'Não foi possível confirmar a retirada.');
      return;
    }
    this.key ??= crypto.randomUUID();
    this.run(this.api.lend(reader.id, book.id, this.key), (loan) => this.completed.emit(loan),
      'Não foi possível registrar o empréstimo.');
  }

  private resetPreparation(): void {
    this.preparation.set(null);
    this.identityChecked.set(false);
    this.error.set('');
    this.key = null;
  }

  private run<T>(request: Observable<T>, success: (result: T) => void, fallback: string): void {
    if (this.busy()) return;
    this.busy.set(true);
    this.error.set('');
    request.pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.busy.set(false))).subscribe({
      next: (result) => success(result),
      error: (error: unknown) => this.error.set(apiErrorMessage(error, fallback)),
    });
  }
}
