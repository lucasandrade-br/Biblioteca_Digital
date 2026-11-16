import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { RouterLink, RouterOutlet } from '@angular/router';
import { ListFilters } from '../../../shared/components/list-filters';
import { ListPagination } from '../../../shared/components/list-pagination';
import { createPagedList } from '../../../shared/listing/paged-list';
import { apiErrorMessage } from '../../../core/http/api-error';
import { BookApi } from '../../books/data-access/book-api';
import { BookStatus, BookSummary, Genre } from '../../books/models/book.models';
import { CollectionApi } from '../data-access/collection-api';
import type { BookEditor } from './book-editor';

@Component({
  imports: [ListFilters, ListPagination, ReactiveFormsModule, RouterLink, RouterOutlet],
  template: `
    <section class="page">
      <div class="page-heading">
        <div>
          <span class="section-kicker">GESTÃO</span>
          <h1 tabindex="-1">Acervo</h1>
          <p class="page-intro">Cadastre, edite e inative os livros da biblioteca.</p>
        </div>
        <div class="actions">
          <a class="button-primary" routerLink="/acervo/novo">Novo livro</a>
        </div>
      </div>

      <form appListFilters [formGroup]="filters" (ngSubmit)="list.apply(filters.getRawValue())" (clear)="clearFilters()">
        <div class="field">
          <label for="q">Buscar</label>
          <input id="q" type="search" formControlName="q" placeholder="Título, autor ou ISBN" />
        </div>
        <div class="field">
          <label for="filter-genre">Gênero</label>
          <select id="filter-genre" formControlName="genreId">
            <option [ngValue]="null">Todos</option>
            @for (genre of genres(); track genre.id) {
              <option [ngValue]="genre.id">{{ genre.name }}</option>
            }
          </select>
        </div>
        <div class="field">
          <label for="status">Situação</label>
          <select id="status" formControlName="status">
            <option [ngValue]="null">Todas</option>
            <option value="ACTIVE">Ativo</option>
            <option value="INACTIVE">Inativo</option>
          </select>
        </div>
      </form>

      @if (message(); as current) {
        <div class="notice" [class.success]="current.ok" [class.error]="!current.ok" role="status">{{ current.text }}</div>
      }

      @if (list.loading()) {
        <p class="page-message" role="status">Carregando acervo...</p>
      } @else if (list.error()) {
        <div class="notice error" role="alert">{{ list.error() }}</div>
      } @else if (list.items().length === 0) {
        <div class="empty-state"><h2>Nenhum livro encontrado.</h2></div>
      }
      @if (!list.error() && list.items().length) {
        <div class="table-wrap panel">
          <table>
            <thead>
              <tr><th>Título</th><th>Autores</th><th>Gênero</th><th>Disponíveis</th><th>Situação</th><th></th></tr>
            </thead>
            <tbody>
              @for (book of list.items(); track book.id) {
                <tr>
                  <td>{{ book.titulo }}</td>
                  <td>{{ book.authors.join(', ') }}</td>
                  <td>{{ book.genre }}</td>
                  <td>{{ book.availableCopies }}</td>
                  <td>
                    <span [class]="'status-badge status-' + (book.status === 'ACTIVE' ? 'active' : 'cancelled')">
                      {{ book.status === 'ACTIVE' ? 'Ativo' : 'Inativo' }}
                    </span>
                  </td>
                  <td class="row-actions">
                    <a class="button-secondary small" [routerLink]="['/acervo', book.id, 'editar']">Editar</a>
                    <button type="button" class="button-secondary small" [disabled]="busyId() === book.id" (click)="toggle(book)">
                      {{ book.status === 'ACTIVE' ? 'Inativar' : 'Reativar' }}
                    </button>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
      @if (!list.error()) {
        <app-list-pagination label="Paginação do acervo" [state]="list.pagination()"
          (pageChange)="list.load($event)" (sizeChange)="list.resize($event)" />
      }
    </section>
    <router-outlet (activate)="connectEditor($event)" />
  `,
})
export class CollectionBooks implements OnInit {
  private readonly api = inject(CollectionApi);
  private readonly destroyRef = inject(DestroyRef);
  private readonly bookApi = inject(BookApi);
  protected readonly filters = inject(FormBuilder).nonNullable.group({
    q: [''],
    genreId: [null as number | null],
    status: [null as BookStatus | null],
  });
  protected readonly genres = signal<Genre[]>([]);
  protected readonly list = createPagedList(
    this.filters.getRawValue(),
    (filter, page, size) => this.api.list(filter, page, size),
    () => 'Não foi possível carregar o acervo.',
  );
  protected readonly busyId = signal<number | null>(null);
  protected readonly message = signal<{ ok: boolean; text: string } | null>(null);

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

  protected connectEditor(editor: BookEditor): void {
    editor.saved.subscribe(() => {
      this.message.set({ ok: true, text: 'Livro salvo.' });
      this.list.refresh();
    });
  }

  protected toggle(book: BookSummary): void {
    const deactivating = book.status === 'ACTIVE';
    this.busyId.set(book.id);
    (deactivating ? this.api.deactivate(book.id) : this.api.activate(book.id)).subscribe({
      next: () => {
        this.list.items.update((books) => books.map((current) =>
          current.id === book.id ? { ...current, status: deactivating ? 'INACTIVE' : 'ACTIVE' } : current));
        this.message.set({ ok: true, text: `"${book.titulo}" ${deactivating ? 'inativado' : 'reativado'}.` });
        this.busyId.set(null);
        if (this.list.filter().status) this.list.refresh();
      },
      error: (error: unknown) => {
        this.message.set({ ok: false, text: apiErrorMessage(error, 'Não foi possível alterar o livro.') });
        this.busyId.set(null);
      },
    });
  }
}
