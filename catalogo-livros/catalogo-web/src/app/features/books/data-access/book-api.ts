import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { API_URL } from '../../../core/http/api-url';
import { Author, Book, BookFilter, BookSummary, Genre } from '../models/book.models';
import { Page } from '../../../core/http/page';

@Injectable({ providedIn: 'root' })
export class BookApi {
  private readonly http = inject(HttpClient);
  private readonly api = inject(API_URL);

  list(filter: BookFilter, page = 0, size = 12) {
    let params = new HttpParams().set('page', page).set('size', size).set('sort', 'titulo,asc');
    if (filter.q?.trim()) params = params.set('q', filter.q.trim());
    if (filter.genreId) params = params.set('genreId', filter.genreId);
    if (filter.disponiveis) params = params.set('disponiveis', true);
    return this.http.get<Page<BookSummary>>(`${this.api}/livros`, { params });
  }

  get(id: number) {
    return this.http.get<Book>(`${this.api}/livros/${id}`);
  }

  listGenres() {
    return this.http.get<Genre[]>(`${this.api}/generos`);
  }

  listAuthors() {
    return this.http.get<Author[]>(`${this.api}/autores`);
  }
}
