import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpContext, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { catchError, map, switchMap, throwError } from 'rxjs';
import { VERIFY_SESSION_ON_UNAUTHORIZED } from '../../../core/auth/auth.interceptor';
import { API_URL } from '../../../core/http/api-url';
import { Author, Book, BookSummary, CollectionBookFilter, Genre } from '../../books/models/book.models';
import { Page } from '../../../core/http/page';

export interface BookPayload {
  titulo: string;
  isbn: string;
  publisher: string;
  publicationYear: number;
  numeroPaginas: number;
  synopsis: string;
  coverUrl: string | null;
  genreId: number;
  authorIds: number[];
  totalCopies: number;
}

@Injectable({ providedIn: 'root' })
export class CollectionApi {
  private readonly http = inject(HttpClient);
  private readonly api = inject(API_URL);

  list(filter: CollectionBookFilter, page: number, size: number) {
    let params = new HttpParams().set('page', page).set('size', size).set('sort', 'titulo,asc');
    if (filter.q?.trim()) params = params.set('q', filter.q.trim());
    if (filter.genreId) params = params.set('genreId', filter.genreId);
    if (filter.status) params = params.set('status', filter.status);
    return this.http.get<Page<BookSummary>>(`${this.api}/livros/acervo`, { params });
  }

  get(id: number) {
    return this.http.get<Book>(`${this.api}/livros/acervo/${id}`);
  }

  create(book: BookPayload) {
    return this.http.post<Book>(`${this.api}/livros`, book);
  }

  update(id: number, book: BookPayload) {
    return this.http.put<Book>(`${this.api}/livros/${id}`, book);
  }

  deactivate(id: number) {
    return this.http.patch<void>(`${this.api}/livros/${id}/inativar`, {});
  }

  activate(id: number) {
    return this.http.patch<void>(`${this.api}/livros/${id}/reativar`, {});
  }

  listAuthors(q: string, page: number, size: number) {
    return this.listReference<Author>('autores', q, page, size);
  }

  listGenres(q: string, page: number, size: number) {
    return this.listReference<Genre>('generos', q, page, size);
  }

  createGenre(name: string) {
    return this.http.post<Genre>(`${this.api}/acervo/generos`, { name });
  }

  updateGenre(id: number, name: string) {
    return this.http.put<Genre>(`${this.api}/acervo/generos/${id}`, { name });
  }

  deleteGenre(id: number) {
    return this.http.delete<void>(`${this.api}/acervo/generos/${id}`);
  }

  createAuthor(name: string, nationality: string) {
    return this.http.post<Author>(`${this.api}/acervo/autores`, { name, nationality });
  }

  updateAuthor(id: number, name: string, nationality: string | null) {
    return this.http.put<Author>(`${this.api}/acervo/autores/${id}`, { name, nationality });
  }

  deleteAuthor(id: number) {
    return this.http.delete<void>(`${this.api}/acervo/autores/${id}`);
  }

  private listReference<T extends Genre & { nationality?: string | null }>(
    resource: 'autores' | 'generos', q: string, page: number, size: number,
  ) {
    const query = q.trim();
    return this.http.get<Page<T>>(`${this.api}/acervo/${resource}`, {
      params: { q: query, page, size, sort: 'name,asc' },
      context: new HttpContext().set(VERIFY_SESSION_ON_UNAUTHORIZED, true),
    }).pipe(
      catchError((error: unknown) => {
        if (!(error instanceof HttpErrorResponse) || ![401, 404, 405].includes(error.status)) {
          return throwError(() => error);
        }

        // Older APIs can return 401 for the missing GET route even with a valid token.
        // Check access to the protected collection before using the public reference list.
        return this.list({ status: null }, 0, 1).pipe(
          switchMap(() => this.http.get<T[]>(`${this.api}/${resource}`)),
          map((records): Page<T> => {
            const term = query.toLowerCase();
            const matches = records.filter((record) =>
              record.name.toLowerCase().includes(term) ||
              record.nationality?.toLowerCase().includes(term),
            );
            return {
              content: matches.slice(page * size, (page + 1) * size),
              number: page,
              size,
              totalPages: Math.ceil(matches.length / size),
              totalElements: matches.length,
            };
          }),
        );
      }),
    );
  }
}
