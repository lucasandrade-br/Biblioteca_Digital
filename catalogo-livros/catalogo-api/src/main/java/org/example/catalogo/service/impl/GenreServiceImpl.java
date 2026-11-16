package org.example.catalogo.service.impl;

import org.example.catalogo.model.Genre;
import org.example.catalogo.repository.BookRepository;
import org.example.catalogo.repository.GenreRepository;
import org.example.catalogo.service.GenreService;
import org.example.catalogo.service.dto.BookContracts.GenreView;
import org.example.catalogo.service.dto.PageQuery;
import org.example.catalogo.service.dto.PageResult;
import org.example.catalogo.service.exception.ApplicationServiceException;
import org.example.catalogo.service.mapper.PageableMapper;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class GenreServiceImpl implements GenreService {

    private final GenreRepository genreRepository;
    private final BookRepository bookRepository;

    public GenreServiceImpl(GenreRepository genreRepository, BookRepository bookRepository) {
        this.genreRepository = genreRepository;
        this.bookRepository = bookRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<GenreView> list() {
        return genreRepository.findAll(Sort.by("name")).stream().map(this::toView).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<GenreView> list(String term, PageQuery query) {
        var page = genreRepository.findByNameContainingIgnoreCase(term == null ? "" : term.trim(), PageableMapper.toPageable(query));
        return PageableMapper.toResult(page.map(this::toView), query);
    }

    @Override
    @Transactional
    public GenreView create(String name) {
        String normalized = name.trim();
        validateUniqueName(normalized, null);
        return toView(genreRepository.save(new Genre(normalized)));
    }

    @Override
    @Transactional
    public GenreView update(Long genreId, String name) {
        Genre genre = findGenre(genreId);
        String normalized = name.trim();
        validateUniqueName(normalized, genreId);
        genre.setName(normalized);
        return toView(genre);
    }

    @Override
    @Transactional
    public void delete(Long genreId) {
        Genre genre = findGenre(genreId);
        if (bookRepository.existsByGenre_Id(genreId)) {
            throw new IllegalArgumentException("Gênero possui livros vinculados e não pode ser excluído");
        }
        genreRepository.delete(genre);
    }

    private void validateUniqueName(String name, Long currentId) {
        genreRepository.findByNameIgnoreCase(name)
            .filter(existing -> !existing.getId().equals(currentId))
            .ifPresent(existing -> {
                throw new IllegalArgumentException("Gênero já cadastrado");
            });
    }

    private Genre findGenre(Long genreId) {
        return genreRepository.findById(genreId)
            .orElseThrow(() -> new ApplicationServiceException(
                ApplicationServiceException.Code.NOT_FOUND, "Gênero não encontrado"
            ));
    }

    private GenreView toView(Genre genre) {
        return new GenreView(genre.getId(), genre.getName());
    }
}
