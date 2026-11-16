package org.example.catalogo.controller;

import org.example.catalogo.dto.AuthorResponse;
import org.example.catalogo.dto.BookRequest;
import org.example.catalogo.dto.BookResponse;
import org.example.catalogo.dto.BookSummaryResponse;
import org.example.catalogo.dto.GenreResponse;
import org.example.catalogo.model.enums.BookStatus;
import org.example.catalogo.service.dto.BookContracts.AuthorView;
import org.example.catalogo.service.dto.BookContracts.BookCommand;
import org.example.catalogo.service.dto.BookContracts.BookSummaryView;
import org.example.catalogo.service.dto.BookContracts.BookView;
import org.example.catalogo.service.dto.BookContracts.GenreView;

final class BookHttpMapper {

    private BookHttpMapper() {
    }

    static BookCommand toCommand(BookRequest request) {
        return new BookCommand(
            request.titulo(),
            request.isbn(),
            request.publisher(),
            request.publicationYear(),
            request.numeroPaginas(),
            request.synopsis(),
            request.coverUrl(),
            request.genreId(),
            request.authorIds(),
            request.totalCopies()
        );
    }

    static BookSummaryResponse toSummary(BookSummaryView book) {
        return new BookSummaryResponse(
            book.id(), book.titulo(), book.authors(), book.genre(), book.coverUrl(), book.publicationYear(), book.numeroPaginas(),
            book.availableCopies(), BookStatus.valueOf(book.status())
        );
    }

    static BookResponse toResponse(BookView book) {
        return new BookResponse(
            book.id(), book.titulo(), book.isbn(), book.publisher(), book.publicationYear(), book.numeroPaginas(), book.synopsis(),
            book.coverUrl(), toResponse(book.genre()),
            book.authors().stream().map(BookHttpMapper::toResponse).toList(),
            book.totalCopies(), book.availableCopies(), BookStatus.valueOf(book.status())
        );
    }

    static GenreResponse toResponse(GenreView genre) {
        return new GenreResponse(genre.id(), genre.name());
    }

    static AuthorResponse toResponse(AuthorView author) {
        return new AuthorResponse(author.id(), author.name(), author.nationality());
    }
}
