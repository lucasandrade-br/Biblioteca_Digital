package org.example.catalogo.service.impl;

import org.example.catalogo.model.Author;
import org.example.catalogo.repository.AuthorRepository;
import org.example.catalogo.repository.BookRepository;
import org.example.catalogo.service.AuthorService;
import org.example.catalogo.service.dto.BookContracts.AuthorCommand;
import org.example.catalogo.service.dto.BookContracts.AuthorView;
import org.example.catalogo.service.dto.PageQuery;
import org.example.catalogo.service.dto.PageResult;
import org.example.catalogo.service.exception.ApplicationServiceException;
import org.example.catalogo.service.mapper.PageableMapper;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class AuthorServiceImpl implements AuthorService {

    private final AuthorRepository authorRepository;
    private final BookRepository bookRepository;

    public AuthorServiceImpl(AuthorRepository authorRepository, BookRepository bookRepository) {
        this.authorRepository = authorRepository;
        this.bookRepository = bookRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuthorView> list() {
        return authorRepository.findAll(Sort.by("name")).stream().map(this::toView).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<AuthorView> list(String term, PageQuery query) {
        var page = authorRepository.search(term == null ? "" : term.trim().toLowerCase(Locale.ROOT), PageableMapper.toPageable(query));
        return PageableMapper.toResult(page.map(this::toView), query);
    }

    @Override
    @Transactional
    public AuthorView create(AuthorCommand command) {
        String name = command.name().trim();
        validateUniqueName(name, null);
        return toView(authorRepository.save(new Author(name, command.nationality())));
    }

    @Override
    @Transactional
    public AuthorView update(Long authorId, AuthorCommand command) {
        Author author = findAuthor(authorId);
        String name = command.name().trim();
        validateUniqueName(name, authorId);
        author.update(name, command.nationality());
        return toView(author);
    }

    @Override
    @Transactional
    public void delete(Long authorId) {
        Author author = findAuthor(authorId);
        if (bookRepository.existsByAuthors_Id(authorId)) {
            throw new IllegalArgumentException("Autor possui livros vinculados e não pode ser excluído");
        }
        authorRepository.delete(author);
    }

    private void validateUniqueName(String name, Long currentId) {
        authorRepository.findByNameIgnoreCase(name)
            .filter(existing -> !existing.getId().equals(currentId))
            .ifPresent(existing -> {
                throw new IllegalArgumentException("Autor já cadastrado");
            });
    }

    private Author findAuthor(Long authorId) {
        return authorRepository.findById(authorId)
            .orElseThrow(() -> new ApplicationServiceException(
                ApplicationServiceException.Code.NOT_FOUND, "Autor não encontrado"
            ));
    }

    private AuthorView toView(Author author) {
        return new AuthorView(author.getId(), author.getName(), author.getNationality());
    }
}
