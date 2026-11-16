package org.example.catalogo.service.dto;

import java.util.List;

public final class BookContracts {

    private BookContracts() {
    }

    public record BookCommand(
        String titulo,
        String isbn,
        String publisher,
        int publicationYear,
        int numeroPaginas,
        String synopsis,
        String coverUrl,
        Long genreId,
        List<Long> authorIds,
        int totalCopies
    ) {
        public BookCommand {
            authorIds = List.copyOf(authorIds);
        }
    }

    public record BookSearch(String term, Long genreId, boolean onlyAvailable) {
    }

    public record GenreView(Long id, String name) {
    }

    public record AuthorCommand(String name, String nationality) {
    }

    public record AuthorView(Long id, String name, String nationality) {
    }

    public record BookSummaryView(
        Long id,
        String titulo,
        List<String> authors,
        String genre,
        String coverUrl,
        int publicationYear,
        int numeroPaginas,
        int availableCopies,
        String status
    ) {
        public BookSummaryView {
            authors = List.copyOf(authors);
        }
    }

    public record BookView(
        Long id,
        String titulo,
        String isbn,
        String publisher,
        int publicationYear,
        int numeroPaginas,
        String synopsis,
        String coverUrl,
        GenreView genre,
        List<AuthorView> authors,
        int totalCopies,
        int availableCopies,
        String status
    ) {
        public BookView {
            authors = List.copyOf(authors);
        }
    }
}
