package org.example.catalogo.repository;

import org.example.catalogo.model.Usuario;
import jakarta.persistence.LockModeType;
import org.example.catalogo.model.enums.UsuarioPerfil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    @EntityGraph(attributePaths = "perfis")
    Optional<Usuario> findByEmailIgnoreCase(String email);

    @Query("""
        SELECT u FROM Usuario u
        WHERE (:active IS NULL OR u.ativo = :active)
          AND (:role IS NULL OR :role MEMBER OF u.perfis)
          AND (:term = '' OR LOCATE(:term, LOWER(u.nome)) > 0 OR LOCATE(:term, LOWER(u.email)) > 0)
        """)
    Page<Usuario> search(
        @Param("term") String term, @Param("role") UsuarioPerfil role,
        @Param("active") Boolean active, Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Usuario> findWithLockById(Long id);
}
