package org.example.catalogo.service;

import org.example.catalogo.service.dto.BookContracts.GenreView;
import org.example.catalogo.service.dto.PageQuery;
import org.example.catalogo.service.dto.PageResult;

import java.util.List;

public interface GenreService {

    List<GenreView> list();

    PageResult<GenreView> list(String term, PageQuery query);

    GenreView create(String name);

    GenreView update(Long genreId, String name);

    void delete(Long genreId);
}
