package org.example.catalogo.controller;

import org.example.catalogo.dto.GenreRequest;
import org.example.catalogo.dto.GenreResponse;
import org.example.catalogo.service.GenreService;
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
public class GenreController {

    private final GenreService genreService;

    public GenreController(GenreService genreService) {
        this.genreService = genreService;
    }

    @GetMapping("/api/generos")
    public ResponseEntity<List<GenreResponse>> list() {
        return ResponseEntity.ok(genreService.list().stream().map(BookHttpMapper::toResponse).toList());
    }

    @PreAuthorize(SECURITY_ROLE_STAFF)
    @GetMapping("/api/acervo/generos")
    public ResponseEntity<Page<GenreResponse>> listCollection(
        @RequestParam(required = false) String q,
        @PageableDefault(size = 20, sort = "name") Pageable pageable
    ) {
        var result = genreService.list(q, PageHttpMapper.toQuery(pageable));
        return ResponseEntity.ok(PageHttpMapper.toPage(result, BookHttpMapper::toResponse));
    }

    @PreAuthorize(SECURITY_ROLE_STAFF)
    @PostMapping("/api/acervo/generos")
    public ResponseEntity<GenreResponse> create(@RequestBody @Valid GenreRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(BookHttpMapper.toResponse(genreService.create(request.name())));
    }

    @PreAuthorize(SECURITY_ROLE_STAFF)
    @PutMapping("/api/acervo/generos/{genreId}")
    public ResponseEntity<GenreResponse> update(@PathVariable Long genreId, @RequestBody @Valid GenreRequest request) {
        return ResponseEntity.ok(BookHttpMapper.toResponse(genreService.update(genreId, request.name())));
    }

    @PreAuthorize(SECURITY_ROLE_STAFF)
    @DeleteMapping("/api/acervo/generos/{genreId}")
    public ResponseEntity<Void> delete(@PathVariable Long genreId) {
        genreService.delete(genreId);
        return ResponseEntity.noContent().build();
    }
}
