import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { BookApi } from '../data-access/book-api';
import { Genre } from '../models/book.models';
import { BookCover } from '../../../shared/components/book-cover';
import { ListFilters } from '../../../shared/components/list-filters';
import { ListPagination } from '../../../shared/components/list-pagination';
import { createPagedList } from '../../../shared/listing/paged-list';

@Component({
  imports: [ListFilters, ListPagination, ReactiveFormsModule, RouterLink, BookCover],
  template: `
    <section class="page">
      <div class="page-heading">
        <div>
          <span class="section-kicker">ACERVO</span>
          <h1>Encontre seu próximo livro</h1>
          <p class="page-intro">Busque por título, autor ou ISBN e reserve um exemplar para retirar no balcão.</p>
        </div>
      </div>

      <form appListFilters [formGroup]="filters" (ngSubmit)="list.apply(filters.getRawValue())" (clear)="clearFilters()">
        <div class="field">
          <label for="q">Buscar</label>
          <input id="q" type="search" formControlName="q" placeholder="Título, autor ou ISBN" />
        </div>
        <div class="field">
          <label for="genreId">Gênero</label>
          <select id="genreId" formControlName="genreId">
            <option [ngValue]="null">Todos</option>
            @for (genre of genres(); track genre.id) {
              <option [ngValue]="genre.id">{{ genre.name }}</option>
            }
          </select>
        </div>
        <label class="check-field">
          <input type="checkbox" formControlName="disponiveis" /> Só disponíveis
        </label>
      </form>

      @if (list.loading()) {
        <p class="page-message" role="status">Carregando livros...</p>
      } @else if (list.error()) {
        <div class="notice error" role="alert">{{ list.error() }}</div>
      } @else if (list.items().length === 0) {
        <div class="empty-state">
          <h2>Nenhum livro encontrado.</h2>
          <p>Tente outro termo ou remova os filtros.</p>
        </div>
      } @else {
        <div class="book-grid">
          @for (book of list.items(); track book.id) {
            <a class="book-card" [routerLink]="['/livros', book.id]">
              <app-book-cover [title]="book.titulo" [author]="book.authors.join(', ')" [url]="book.coverUrl" />
              <div class="book-card-body">
                <span class="section-kicker">{{ book.genre }}</span>
                <h2>{{ book.titulo }}</h2>
                <p>{{ book.authors.join(', ') }} · {{ book.publicationYear }} · {{ book.numeroPaginas }} págs.</p>
                @if (book.availableCopies > 0) {
                  <span class="availability ok">{{ book.availableCopies }} disponível(is)</span>
                } @else {
                  <span class="availability none">Todos emprestados</span>
                }
              </div>
            </a>
          }
        </div>
      }
      @if (!list.error()) {
        <app-list-pagination label="Paginação do catálogo" [state]="list.pagination()"
          (pageChange)="list.load($event)" (sizeChange)="list.resize($event)" />
      }
    </section>
  `,
})
export class Catalog implements OnInit {
  private readonly bookApi = inject(BookApi);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly filters = inject(FormBuilder).nonNullable.group({
    q: [''],
    genreId: [null as number | null],
    disponiveis: [false],
  });
  protected readonly genres = signal<Genre[]>([]);
  protected readonly list = createPagedList(
    this.filters.getRawValue(),
    (filter, page, size) => this.bookApi.list(filter, page, size),
    () => 'Não foi possível carregar o catálogo.',
    12,
  );

  ngOnInit(): void {
    this.bookApi.listGenres().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (genres) => this.genres.set(genres),
      error: () => this.genres.set([]),
    });
    this.list.load(0);
  }

  protected clearFilters(): void {
    this.filters.reset();
    this.list.apply(this.filters.getRawValue());
  }
}
