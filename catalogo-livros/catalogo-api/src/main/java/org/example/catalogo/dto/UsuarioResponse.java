package org.example.catalogo.dto;

import org.example.catalogo.model.enums.UsuarioPerfil;

import java.util.Set;

public record UsuarioResponse(
    Long id,
    String nome,
    String email,
    boolean ativo,
    Set<UsuarioPerfil> perfis
) {
}
