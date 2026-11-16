export type BookStatus = 'ACTIVE' | 'INACTIVE';

export interface Genre {
  id: number;
  name: string;
}

export interface Author {
  id: number;
  name: string;
  nationality: string | null;
}

export interface BookSummary {
  id: number;
  titulo: string;
  authors: string[];
  genre: string;
  coverUrl: string | null;
  publicationYear: number;
  numeroPaginas: number;
  availableCopies: number;
  status: BookStatus;
}

export interface Book {
  id: number;
  titulo: string;
  isbn: string;
  publisher: string;
  publicationYear: number;
  numeroPaginas: number;
  synopsis: string;
  coverUrl: string | null;
  genre: Genre;
  authors: Author[];
  totalCopies: number;
  availableCopies: number;
  status: BookStatus;
}

export interface CollectionBookFilter extends BookFilter {
  status: BookStatus | null;
}

export interface BookFilter {
  q?: string;
  genreId?: number | null;
  disponiveis?: boolean;
}
