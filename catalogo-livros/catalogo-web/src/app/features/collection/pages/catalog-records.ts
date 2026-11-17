import { RowAction } from '../../../shared/components/row-action';
import { Component, DestroyRef, OnInit, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { Observable } from 'rxjs';
import { ListFilters } from '../../../shared/components/list-filters';
import { ListPagination } from '../../../shared/components/list-pagination';
import { createPagedList } from '../../../shared/listing/paged-list';
import { apiErrorMessage } from '../../../core/http/api-error';
import { Modal } from '../../../shared/components/modal';
import { notBlank } from '../../../shared/forms/not-blank';
import { Author, Genre } from '../../books/models/book.models';
import { CollectionApi } from '../data-access/collection-api';

const LABELS = {
  author: {
    heading: 'Autores',
    create: 'Novo autor',
    edit: 'Editar autor',
    saved: 'Autor salvo.',
    deleted: 'Autor excluído.',
    empty: 'Nenhum autor cadastrado.',
    loadError: 'Não foi possível carregar os autores.',
    nameMax: 150,
  },
  genre: {
    heading: 'Gêneros',
    create: 'Novo gênero',
    edit: 'Editar gênero',
    saved: 'Gênero salvo.',
    deleted: 'Gênero excluído.',
    empty: 'Nenhum gênero cadastrado.',
    loadError: 'Não foi possível carregar os gêneros.',
    nameMax: 80,
  },
};

type CatalogRecord = Genre & { nationality?: Author['nationality'] };

@Component({
  selector: 'app-catalog-records',
  imports: [RowAction, ListFilters, ListPagination, Modal, ReactiveFormsModule],
  templateUrl: './catalog-records.html',
})
export class CatalogRecords implements OnInit {
  readonly kind = input.required<keyof typeof LABELS>();
  private readonly api = inject(CollectionApi);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly labels = computed(() => LABELS[this.kind()]);
  protected readonly filters = inject(FormBuilder).nonNullable.group({ q: [''] });
  protected readonly list = createPagedList<CatalogRecord, { q: string }>(
    this.filters.getRawValue(),
    (filter, page, size) => this.kind() === 'author'
      ? this.api.listAuthors(filter.q, page, size)
      : this.api.listGenres(filter.q, page, size),
    () => this.labels().loadError,
  );
  protected readonly message = signal<{ ok: boolean; text: string } | null>(null);
  protected readonly editorOpen = signal(false);
  protected readonly pending = signal(false);
  protected readonly deleting = signal(false);
  protected readonly editorError = signal('');
  protected readonly editingId = signal<number | null>(null);
  protected readonly form = inject(FormBuilder).nonNullable.group({
    name: ['', notBlank],
    nationality: [''],
  });

  ngOnInit(): void {
    this.list.load(0);
  }

  protected openEditor(record?: CatalogRecord): void {
    this.editingId.set(record?.id ?? null);
    this.form.reset({ name: record?.name ?? '', nationality: record?.nationality ?? '' });
    this.editorError.set('');
    this.editorOpen.set(true);
  }

  protected save(): void {
    if (this.pending()) return;
    this.form.markAllAsTouched();
    if (this.form.invalid) return;

    const id = this.editingId();
    const { name, nationality } = this.form.getRawValue();
    this.pending.set(true);
    this.editorError.set('');
    this.saveRecord(id, name.trim(), nationality.trim())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.pending.set(false);
          this.editorOpen.set(false);
          this.message.set({ ok: true, text: this.labels().saved });
          this.list.refresh();
        },
        error: (error: unknown) => {
          this.pending.set(false);
          this.editorError.set(apiErrorMessage(error, 'Não foi possível salvar o registro.'));
        },
      });
  }

  protected delete(record: CatalogRecord): void {
    if (this.deleting()) return;
    this.deleting.set(true);
    this.message.set(null);
    const request = this.kind() === 'author'
      ? this.api.deleteAuthor(record.id)
      : this.api.deleteGenre(record.id);
    request.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.deleting.set(false);
        this.message.set({ ok: true, text: this.labels().deleted });
        this.list.refresh();
      },
      error: (error: unknown) => {
        this.deleting.set(false);
        this.message.set({ ok: false, text: apiErrorMessage(error, 'Não foi possível excluir o registro.') });
      },
    });
  }

  protected clearFilters(): void {
    this.filters.reset();
    this.list.apply(this.filters.getRawValue());
  }

  private saveRecord(id: number | null, name: string, nationality: string): Observable<Genre | Author> {
    if (this.kind() === 'author') {
      return id === null
        ? this.api.createAuthor(name, nationality)
        : this.api.updateAuthor(id, name, nationality || null);
    }
    return id === null ? this.api.createGenre(name) : this.api.updateGenre(id, name);
  }
}
