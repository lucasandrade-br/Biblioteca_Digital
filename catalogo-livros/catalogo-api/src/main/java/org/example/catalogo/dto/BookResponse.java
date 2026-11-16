package org.example.catalogo.dto;

import org.example.catalogo.model.enums.BookStatus;

import java.util.List;

public record BookResponse(
    Long id,
    String titulo,
    String isbn,
    String publisher,
    int publicationYear,
    int numeroPaginas,
    String synopsis,
    String coverUrl,
    GenreResponse genre,
    List<AuthorResponse> authors,
    int totalCopies,
    int availableCopies,
    BookStatus status
) {
}
