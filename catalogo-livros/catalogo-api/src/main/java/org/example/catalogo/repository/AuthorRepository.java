package org.example.catalogo.repository;

import org.example.catalogo.model.Author;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AuthorRepository extends JpaRepository<Author, Long> {

    @Query("""
        SELECT a FROM Author a
        WHERE :term = '' OR LOCATE(:term, LOWER(a.name)) > 0
          OR LOCATE(:term, LOWER(a.nationality)) > 0
        """)
    Page<Author> search(@Param("term") String term, Pageable pageable);

    Optional<Author> findByNameIgnoreCase(String name);
}
