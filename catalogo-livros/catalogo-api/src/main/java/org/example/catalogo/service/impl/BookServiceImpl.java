package org.example.catalogo.service.impl;

import org.example.catalogo.model.Author;
import org.example.catalogo.model.Book;
import org.example.catalogo.model.Genre;
import org.example.catalogo.model.enums.BookStatus;
import org.example.catalogo.model.enums.LoanStatus;
import org.example.catalogo.repository.AuthorRepository;
import org.example.catalogo.repository.BookRepository;
import org.example.catalogo.repository.GenreRepository;
import org.example.catalogo.repository.LoanRepository;
import org.example.catalogo.service.BookService;
import org.example.catalogo.service.dto.BookContracts.AuthorView;
import org.example.catalogo.service.dto.BookContracts.BookCommand;
import org.example.catalogo.service.dto.BookContracts.BookSearch;
import org.example.catalogo.service.dto.BookContracts.BookSummaryView;
import org.example.catalogo.service.dto.BookContracts.BookView;
import org.example.catalogo.service.dto.BookContracts.GenreView;
import org.example.catalogo.service.dto.PageQuery;
import org.example.catalogo.service.dto.PageResult;
import org.example.catalogo.service.exception.ApplicationServiceException;
import org.example.catalogo.service.mapper.PageableMapper;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.Year;
import java.util.List;
import java.util.Locale;

@Service
public class BookServiceImpl implements BookService {

    private static final int FIRST_PUBLICATION_YEAR = 1450;
    private static final int MAX_PAGES = 5000;

    private final BookRepository bookRepository;
    private final GenreRepository genreRepository;
    private final AuthorRepository authorRepository;
    private final LoanRepository loanRepository;

    public BookServiceImpl(
        BookRepository bookRepository,
        GenreRepository genreRepository,
        AuthorRepository authorRepository,
        LoanRepository loanRepository
    ) {
        this.bookRepository = bookRepository;
        this.genreRepository = genreRepository;
        this.authorRepository = authorRepository;
        this.loanRepository = loanRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<BookSummaryView> listPublic(BookSearch search, PageQuery query) {
        return list(BookStatus.ACTIVE, search, query);
    }

    @Override
    @Transactional(readOnly = true)
    public BookView getPublic(Long bookId) {
        return bookRepository.findByIdAndStatus(bookId, BookStatus.ACTIVE)
            .map(this::toView)
            .orElseThrow(this::bookNotFound);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<BookSummaryView> listCollection(BookSearch search, String status, PageQuery query) {
        return list(status == null ? null : BookStatus.valueOf(status), search, query);
    }

    @Override
    @Transactional(readOnly = true)
    public BookView getFromCollection(Long bookId) {
        return toView(findBook(bookId));
    }

    @Override
    @Transactional
    public BookView create(BookCommand command) {
        String isbn = normalizeIsbn(command.isbn());
        validateCommand(command, isbn, null);
        Book book = new Book(
            command.titulo().trim(),
            isbn,
            command.publisher().trim(),
            command.publicationYear(),
            command.numeroPaginas(),
            command.synopsis().trim(),
            blankToNull(command.coverUrl()),
            findGenre(command.genreId()),
            findAuthors(command.authorIds()),
            command.totalCopies(),
            Instant.now()
        );
        return toView(bookRepository.save(book));
    }

    @Override
    @Transactional
    public BookView update(Long bookId, BookCommand command) {
        Book book = bookRepository.findWithLockById(bookId).orElseThrow(this::bookNotFound);
        String isbn = normalizeIsbn(command.isbn());
        validateCommand(command, isbn, bookId);
        book.update(
            command.titulo().trim(),
            isbn,
            command.publisher().trim(),
            command.publicationYear(),
            command.numeroPaginas(),
            command.synopsis().trim(),
            blankToNull(command.coverUrl()),
            findGenre(command.genreId()),
            findAuthors(command.authorIds()),
            command.totalCopies(),
            Instant.now()
        );
        return toView(book);
    }

    @Override
    @Transactional
    public void deactivate(Long bookId) {
        Book book = bookRepository.findWithLockById(bookId).orElseThrow(this::bookNotFound);
        if (loanRepository.existsByBook_IdAndStatusIn(bookId, LoanStatus.OPEN_STATUSES)) {
            throw new IllegalArgumentException("Livro possui reservas ou empréstimos em aberto");
        }
        book.deactivate(Instant.now());
    }

    @Override
    @Transactional
    public void activate(Long bookId) {
        Book book = bookRepository.findWithLockById(bookId).orElseThrow(this::bookNotFound);
        book.activate(Instant.now());
    }

    private PageResult<BookSummaryView> list(BookStatus status, BookSearch search, PageQuery query) {
        String term = search.term() == null ? "" : search.term().trim().toLowerCase(Locale.ROOT);
        Page<Book> page = bookRepository.search(
            status,
            search.genreId(),
            search.onlyAvailable(),
            "%" + term + "%",
            PageableMapper.toPageable(query)
        );
        return new PageResult<>(
            page.stream().map(this::toSummary).toList(),
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            query.sort()
        );
    }

    private void validateCommand(BookCommand command, String isbn, Long currentId) {
        if (command.numeroPaginas() > MAX_PAGES) {
            throw new IllegalArgumentException("O livro pode ter no máximo 5.000 páginas");
        }
        if (!isValidIsbn13(isbn)) {
            throw new IllegalArgumentException("ISBN inválido. Informe um ISBN-13 válido");
        }
        bookRepository.findByIsbn(isbn)
            .filter(existing -> !existing.getId().equals(currentId))
            .ifPresent(existing -> {
                throw new IllegalArgumentException("Já existe um livro cadastrado com este ISBN");
            });
        int currentYear = Year.now().getValue();
        if (command.publicationYear() < FIRST_PUBLICATION_YEAR || command.publicationYear() > currentYear) {
            throw new IllegalArgumentException("Ano de publicação deve estar entre " + FIRST_PUBLICATION_YEAR + " e " + currentYear);
        }
    }

    private boolean isValidIsbn13(String isbn) {
        if (!isbn.matches("\\d{13}")) {
            return false;
        }
        int sum = 0;
        for (int i = 0; i < 13; i++) {
            int digit = isbn.charAt(i) - '0';
            sum += i % 2 == 0 ? digit : digit * 3;
        }
        return sum % 10 == 0;
    }

    private String normalizeIsbn(String isbn) {
        return isbn.replaceAll("[\\s-]", "");
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Genre findGenre(Long genreId) {
        return genreRepository.findById(genreId)
            .orElseThrow(() -> new IllegalArgumentException("Gênero não encontrado"));
    }

    private List<Author> findAuthors(List<Long> authorIds) {
        List<Long> distinctIds = authorIds.stream().distinct().toList();
        List<Author> authors = authorRepository.findAllById(distinctIds);
        if (authors.size() != distinctIds.size()) {
            throw new IllegalArgumentException("Um ou mais autores não existem");
        }
        return authors;
    }

    private Book findBook(Long bookId) {
        return bookRepository.findById(bookId).orElseThrow(this::bookNotFound);
    }

    private ApplicationServiceException bookNotFound() {
        return new ApplicationServiceException(ApplicationServiceException.Code.NOT_FOUND, "Livro não encontrado");
    }

    private BookSummaryView toSummary(Book book) {
        return new BookSummaryView(
            book.getId(),
            book.getTitulo(),
            book.getAuthors().stream().map(Author::getName).toList(),
            book.getGenre().getName(),
            book.getCoverUrl(),
            book.getPublicationYear(),
            book.getNumeroPaginas(),
            book.getAvailableCopies(),
            book.getStatus().name()
        );
    }

    private BookView toView(Book book) {
        return new BookView(
            book.getId(),
            book.getTitulo(),
            book.getIsbn(),
            book.getPublisher(),
            book.getPublicationYear(),
            book.getNumeroPaginas(),
            book.getSynopsis(),
            book.getCoverUrl(),
            new GenreView(book.getGenre().getId(), book.getGenre().getName()),
            book.getAuthors().stream()
                .map(author -> new AuthorView(author.getId(), author.getName(), author.getNationality()))
                .toList(),
            book.getTotalCopies(),
            book.getAvailableCopies(),
            book.getStatus().name()
        );
    }
}
