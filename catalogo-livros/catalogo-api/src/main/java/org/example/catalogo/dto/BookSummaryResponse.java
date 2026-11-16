package org.example.catalogo.dto;

import org.example.catalogo.model.enums.BookStatus;

import java.util.List;

public record BookSummaryResponse(
    Long id,
    String titulo,
    List<String> authors,
    String genre,
    String coverUrl,
    int publicationYear,
    int numeroPaginas,
    int availableCopies,
    BookStatus status
) {
}
