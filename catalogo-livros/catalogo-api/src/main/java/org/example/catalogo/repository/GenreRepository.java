package org.example.catalogo.repository;

import org.example.catalogo.model.Genre;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GenreRepository extends JpaRepository<Genre, Long> {

    Page<Genre> findByNameContainingIgnoreCase(String name, Pageable pageable);

    Optional<Genre> findByNameIgnoreCase(String name);
}
