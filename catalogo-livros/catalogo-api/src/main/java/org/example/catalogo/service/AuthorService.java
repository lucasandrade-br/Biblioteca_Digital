package org.example.catalogo.service;

import org.example.catalogo.service.dto.BookContracts.AuthorCommand;
import org.example.catalogo.service.dto.BookContracts.AuthorView;
import org.example.catalogo.service.dto.PageQuery;
import org.example.catalogo.service.dto.PageResult;

import java.util.List;

public interface AuthorService {

    List<AuthorView> list();

    PageResult<AuthorView> list(String term, PageQuery query);

    AuthorView create(AuthorCommand command);

    AuthorView update(Long authorId, AuthorCommand command);

    void delete(Long authorId);
}
