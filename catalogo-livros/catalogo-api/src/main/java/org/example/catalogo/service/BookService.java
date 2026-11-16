package org.example.catalogo.service;

import org.example.catalogo.service.dto.BookContracts.BookCommand;
import org.example.catalogo.service.dto.BookContracts.BookSearch;
import org.example.catalogo.service.dto.BookContracts.BookSummaryView;
import org.example.catalogo.service.dto.BookContracts.BookView;
import org.example.catalogo.service.dto.PageQuery;
import org.example.catalogo.service.dto.PageResult;

public interface BookService {

    PageResult<BookSummaryView> listPublic(BookSearch search, PageQuery query);

    BookView getPublic(Long bookId);

    PageResult<BookSummaryView> listCollection(BookSearch search, String status, PageQuery query);

    BookView getFromCollection(Long bookId);

    BookView create(BookCommand command);

    BookView update(Long bookId, BookCommand command);

    void deactivate(Long bookId);

    void activate(Long bookId);
}
