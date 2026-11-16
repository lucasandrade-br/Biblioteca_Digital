package org.example.catalogo.repository;

import org.example.catalogo.model.Book;
import org.example.catalogo.model.enums.BookStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface BookRepository extends JpaRepository<Book, Long> {

    @Query("""
        SELECT b FROM Book b
        WHERE (:status IS NULL OR b.status = :status)
          AND (:genreId IS NULL OR b.genre.id = :genreId)
          AND (:onlyAvailable = FALSE OR b.totalCopies > b.loanedCopies)
          AND (LOWER(b.titulo) LIKE :term
            OR b.isbn LIKE :term
            OR EXISTS (SELECT 1 FROM b.authors a WHERE LOWER(a.name) LIKE :term))
        """)
    Page<Book> search(
        @Param("status") BookStatus status,
        @Param("genreId") Long genreId,
        @Param("onlyAvailable") boolean onlyAvailable,
        @Param("term") String term,
        Pageable pageable
    );

    Optional<Book> findByIdAndStatus(Long id, BookStatus status);

    Optional<Book> findByIsbn(String isbn);

    boolean existsByGenre_Id(Long genreId);

    boolean existsByAuthors_Id(Long authorId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Book> findWithLockById(Long id);
}
