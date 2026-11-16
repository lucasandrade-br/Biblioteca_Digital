import { Component, DestroyRef, OnInit, inject, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { EMPTY, catchError, forkJoin, of, switchMap } from 'rxjs';
import { apiErrorMessage } from '../../../core/http/api-error';
import { Modal } from '../../../shared/components/modal';
import { notBlank } from '../../../shared/forms/not-blank';
import { BookApi } from '../../books/data-access/book-api';
import { Author, Genre } from '../../books/models/book.models';
import { CollectionApi } from '../data-access/collection-api';

@Component({
  imports: [Modal, ReactiveFormsModule, RouterLink],
  template: `
    <app-modal [title]="editing() ? 'Editar livro' : 'Novo livro'" [pending]="pending()"
      [wide]="true" (dismissed)="close()">
      @if (error()) {
        <div class="notice error" role="alert">{{ error() }}</div>
      }
      @if (loading()) {
        <p class="page-message" role="status">Carregando…</p>
      } @else if (ready()) {
        @if (!authors().length || !genres().length) {
          <p class="notice" role="status">
            Para cadastrar um livro, adicione pelo menos um
            <a routerLink="/acervo/autores">autor</a> e um
            <a routerLink="/acervo/generos">gênero</a>.
          </p>
        }
        <form class="dialog-form" [formGroup]="form" (ngSubmit)="submit()"
          [attr.aria-busy]="pending()" novalidate>
          <div class="field">
            <label for="titulo">Título</label>
            <input id="titulo" formControlName="titulo" maxlength="200" />
            <small class="field-error">
              @if (invalid('titulo')) { Informe o título. }
            </small>
          </div>
          <div class="form-row">
            <div class="field">
              <label for="isbn">ISBN-13</label>
              <input id="isbn" formControlName="isbn" placeholder="978-85-359-1484-9" maxlength="20" />
              <small class="field-error">
                @if (invalid('isbn')) { Informe o ISBN. }
              </small>
            </div>
            <div class="field">
              <label for="publisher">Editora</label>
              <input id="publisher" formControlName="publisher" maxlength="120" />
              <small class="field-error">
                @if (invalid('publisher')) { Informe a editora. }
              </small>
            </div>
          </div>
          <div class="form-row">
            <div class="field">
              <label for="publicationYear">Ano de publicação</label>
              <input id="publicationYear" type="number" formControlName="publicationYear" />
              <small class="field-error">
                @if (invalid('publicationYear')) { Informe o ano. }
              </small>
            </div>
            <div class="field">
              <label for="numeroPaginas">Número de páginas</label>
              <input id="numeroPaginas" type="number" min="1" max="5000" formControlName="numeroPaginas" />
              <small class="field-error">
                @if (invalid('numeroPaginas')) { Informe de 1 a 5.000 páginas. }
              </small>
            </div>
            <div class="field">
              <label for="totalCopies">Exemplares</label>
              <input id="totalCopies" type="number" min="1" max="500" formControlName="totalCopies" />
              <small class="field-error">
                @if (invalid('totalCopies')) { Informe de 1 a 500 exemplares. }
              </small>
            </div>
            <div class="field">
              <label for="genreId">Gênero</label>
              <select id="genreId" formControlName="genreId">
                <option [ngValue]="null">Selecione</option>
                @for (genre of genres(); track genre.id) {
                  <option [ngValue]="genre.id">{{ genre.name }}</option>
                }
              </select>
              <small class="field-error">
                @if (invalid('genreId')) { Selecione o gênero. }
              </small>
            </div>
          </div>
          <fieldset class="field">
            <legend>Autores</legend>
            <div class="check-list">
              @for (author of authors(); track author.id) {
                <label class="check-field">
                  <input type="checkbox" [checked]="selectedAuthors().includes(author.id)" (change)="toggleAuthor(author.id)" />
                  {{ author.name }}
                </label>
              }
            </div>
            <small class="field-error">
              @if (submitted() && selectedAuthors().length === 0) { Selecione ao menos um autor. }
            </small>
          </fieldset>
          <div class="field">
            <label for="synopsis">Sinopse</label>
            <textarea id="synopsis" rows="5" formControlName="synopsis" maxlength="2000"></textarea>
            <small class="field-error">
              @if (invalid('synopsis')) { Informe a sinopse. }
            </small>
          </div>
          <div class="field">
            <label for="coverUrl">URL da capa (opcional)</label>
            <input id="coverUrl" formControlName="coverUrl" maxlength="500" />
          </div>
          <div class="dialog-actions">
            <button type="button" class="button-secondary" [disabled]="pending()" (click)="close()">
              Cancelar
            </button>
            <button class="button-primary" type="submit"
              [disabled]="pending() || !authors().length || !genres().length">
              {{ pending() ? 'Salvando…' : 'Salvar livro' }}
            </button>
          </div>
        </form>
      }
    </app-modal>
  `,
})
export class BookEditor implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly bookApi = inject(BookApi);
  private readonly api = inject(CollectionApi);
  private readonly destroyRef = inject(DestroyRef);
  private bookId: number | null = null;
  readonly saved = output<void>();
  protected readonly editing = signal(false);
  protected readonly ready = signal(false);
  protected readonly genres = signal<Genre[]>([]);
  protected readonly authors = signal<Author[]>([]);
  protected readonly selectedAuthors = signal<number[]>([]);
  protected readonly loading = signal(true);
  protected readonly pending = signal(false);
  protected readonly submitted = signal(false);
  protected readonly error = signal('');
  protected readonly form = inject(FormBuilder).nonNullable.group({
    titulo: ['', notBlank],
    isbn: ['', notBlank],
    publisher: ['', notBlank],
    publicationYear: [new Date().getFullYear(), Validators.required],
    numeroPaginas: [null as number | null, [Validators.required, Validators.min(1), Validators.max(5000)]],
    totalCopies: [1, [Validators.required, Validators.min(1), Validators.max(500)]],
    genreId: [null as number | null, Validators.required],
    synopsis: ['', notBlank],
    coverUrl: [''],
  });

  ngOnInit(): void {
    this.route.paramMap.pipe(
      switchMap((params) => {
        const id = params.get('id');
        this.bookId = id === null ? null : Number(id);
        this.editing.set(id !== null);
        this.form.reset();
        this.submitted.set(false);
        this.selectedAuthors.set([]);
        this.error.set('');
        this.ready.set(false);
        this.loading.set(true);

        if (this.bookId !== null && (!Number.isSafeInteger(this.bookId) || this.bookId <= 0)) {
          this.error.set('Identificador de livro inválido.');
          this.loading.set(false);
          return EMPTY;
        }

        return forkJoin({
          genres: this.bookApi.listGenres(),
          authors: this.bookApi.listAuthors(),
          book: this.bookId === null ? of(null) : this.api.get(this.bookId),
        }).pipe(
          catchError((error: unknown) => {
            this.error.set(apiErrorMessage(error, 'Não foi possível carregar os dados do formulário.'));
            this.loading.set(false);
            return EMPTY;
          }),
        );
      }),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe(({ genres, authors, book }) => {
      this.genres.set(genres);
      this.authors.set(authors);
      if (book) {
        this.form.patchValue({
          titulo: book.titulo,
          isbn: book.isbn,
          publisher: book.publisher,
          publicationYear: book.publicationYear,
          numeroPaginas: book.numeroPaginas,
          totalCopies: book.totalCopies,
          genreId: book.genre.id,
          synopsis: book.synopsis,
          coverUrl: book.coverUrl ?? '',
        });
        this.selectedAuthors.set(book.authors.map((author) => author.id));
      }
      this.ready.set(true);
      this.loading.set(false);
    });
  }

  protected close(): void {
    if (!this.pending()) void this.router.navigate(['/acervo'], { replaceUrl: true });
  }

  protected invalid(name: keyof typeof this.form.controls): boolean {
    const control = this.form.controls[name];
    return control.touched && control.invalid;
  }

  protected toggleAuthor(id: number): void {
    this.selectedAuthors.update((ids) => ids.includes(id) ? ids.filter((current) => current !== id) : [...ids, id]);
  }

  protected submit(): void {
    if (this.pending() || !this.ready()) return;
    this.submitted.set(true);
    this.form.markAllAsTouched();
    if (this.form.invalid || this.selectedAuthors().length === 0) return;
    this.error.set('');
    this.pending.set(true);
    const value = this.form.getRawValue();
    const payload = {
      titulo: value.titulo.trim(),
      isbn: value.isbn.trim(),
      publisher: value.publisher.trim(),
      publicationYear: value.publicationYear,
      numeroPaginas: value.numeroPaginas!,
      synopsis: value.synopsis.trim(),
      coverUrl: value.coverUrl.trim() || null,
      genreId: value.genreId!,
      authorIds: this.selectedAuthors(),
      totalCopies: value.totalCopies,
    };
    const request = this.bookId === null
      ? this.api.create(payload)
      : this.api.update(this.bookId, payload);
    request.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.pending.set(false);
        this.saved.emit();
        this.close();
      },
      error: (error: unknown) => {
        this.error.set(apiErrorMessage(error, 'Não foi possível salvar o livro.'));
        this.pending.set(false);
      },
    });
  }
}
