package org.example.catalogo.controller;

import org.example.catalogo.dto.BookResponse;
import org.example.catalogo.dto.BookSummaryResponse;
import org.example.catalogo.service.BookService;
import org.example.catalogo.service.dto.BookContracts.BookSearch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/livros")
public class PublicBookController {

    private final BookService bookService;

    public PublicBookController(BookService bookService) {
        this.bookService = bookService;
    }

    @GetMapping
    public ResponseEntity<Page<BookSummaryResponse>> list(
        @RequestParam(required = false) String q,
        @RequestParam(required = false) Long genreId,
        @RequestParam(defaultValue = "false") boolean disponiveis,
        @PageableDefault(size = 12, sort = "titulo") Pageable pageable
    ) {
        var result = bookService.listPublic(new BookSearch(q, genreId, disponiveis), PageHttpMapper.toQuery(pageable));
        return ResponseEntity.ok(PageHttpMapper.toPage(result, BookHttpMapper::toSummary));
    }

    @GetMapping("/{bookId}")
    public ResponseEntity<BookResponse> get(@PathVariable Long bookId) {
        return ResponseEntity.ok(BookHttpMapper.toResponse(bookService.getPublic(bookId)));
    }
}
