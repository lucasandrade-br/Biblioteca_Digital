package org.example.catalogo.controller;

import org.example.catalogo.dto.AuthorRequest;
import org.example.catalogo.dto.AuthorResponse;
import org.example.catalogo.service.AuthorService;
import org.example.catalogo.service.dto.BookContracts.AuthorCommand;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.example.catalogo.config.Constants.SECURITY_ROLE_STAFF;

@RestController
public class AuthorController {

    private final AuthorService authorService;

    public AuthorController(AuthorService authorService) {
        this.authorService = authorService;
    }

    @GetMapping("/api/autores")
    public ResponseEntity<List<AuthorResponse>> list() {
        return ResponseEntity.ok(authorService.list().stream().map(BookHttpMapper::toResponse).toList());
    }

    @PreAuthorize(SECURITY_ROLE_STAFF)
    @GetMapping("/api/acervo/autores")
    public ResponseEntity<Page<AuthorResponse>> listCollection(
        @RequestParam(required = false) String q,
        @PageableDefault(size = 20, sort = "name") Pageable pageable
    ) {
        var result = authorService.list(q, PageHttpMapper.toQuery(pageable));
        return ResponseEntity.ok(PageHttpMapper.toPage(result, BookHttpMapper::toResponse));
    }

    @PreAuthorize(SECURITY_ROLE_STAFF)
    @PostMapping("/api/acervo/autores")
    public ResponseEntity<AuthorResponse> create(@RequestBody @Valid AuthorRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(BookHttpMapper.toResponse(
            authorService.create(new AuthorCommand(request.name(), request.nationality()))
        ));
    }

    @PreAuthorize(SECURITY_ROLE_STAFF)
    @PutMapping("/api/acervo/autores/{authorId}")
    public ResponseEntity<AuthorResponse> update(@PathVariable Long authorId, @RequestBody @Valid AuthorRequest request) {
        return ResponseEntity.ok(BookHttpMapper.toResponse(
            authorService.update(authorId, new AuthorCommand(request.name(), request.nationality()))
        ));
    }

    @PreAuthorize(SECURITY_ROLE_STAFF)
    @DeleteMapping("/api/acervo/autores/{authorId}")
    public ResponseEntity<Void> delete(@PathVariable Long authorId) {
        authorService.delete(authorId);
        return ResponseEntity.noContent().build();
    }
}
