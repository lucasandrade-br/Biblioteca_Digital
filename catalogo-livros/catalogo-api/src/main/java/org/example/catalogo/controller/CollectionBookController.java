package org.example.catalogo.controller;

import org.example.catalogo.dto.BookRequest;
import org.example.catalogo.dto.BookResponse;
import org.example.catalogo.dto.BookSummaryResponse;
import org.example.catalogo.model.enums.BookStatus;
import org.example.catalogo.service.BookService;
import org.example.catalogo.service.dto.BookContracts.BookSearch;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.example.catalogo.config.Constants.SECURITY_ROLE_STAFF;

@RestController
@RequestMapping("/api/livros")
@PreAuthorize(SECURITY_ROLE_STAFF)
public class CollectionBookController {

    private final BookService bookService;

    public CollectionBookController(BookService bookService) {
        this.bookService = bookService;
    }

    @GetMapping("/acervo")
    public ResponseEntity<Page<BookSummaryResponse>> list(
        @RequestParam(required = false) String q,
        @RequestParam(required = false) Long genreId,
        @RequestParam(required = false) BookStatus status,
        @PageableDefault(size = 20, sort = "titulo") Pageable pageable
    ) {
        var result = bookService.listCollection(
            new BookSearch(q, genreId, false), status == null ? null : status.name(), PageHttpMapper.toQuery(pageable)
        );
        return ResponseEntity.ok(PageHttpMapper.toPage(result, BookHttpMapper::toSummary));
    }

    @GetMapping("/acervo/{bookId}")
    public ResponseEntity<BookResponse> get(@PathVariable Long bookId) {
        return ResponseEntity.ok(BookHttpMapper.toResponse(bookService.getFromCollection(bookId)));
    }

    @PostMapping
    public ResponseEntity<BookResponse> create(@RequestBody @Valid BookRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(BookHttpMapper.toResponse(bookService.create(BookHttpMapper.toCommand(request))));
    }

    @PutMapping("/{bookId}")
    public ResponseEntity<BookResponse> update(@PathVariable Long bookId, @RequestBody @Valid BookRequest request) {
        return ResponseEntity.ok(BookHttpMapper.toResponse(bookService.update(bookId, BookHttpMapper.toCommand(request))));
    }

    @PatchMapping("/{bookId}/inativar")
    public ResponseEntity<Void> deactivate(@PathVariable Long bookId) {
        bookService.deactivate(bookId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{bookId}/reativar")
    public ResponseEntity<Void> activate(@PathVariable Long bookId) {
        bookService.activate(bookId);
        return ResponseEntity.noContent().build();
    }
}
