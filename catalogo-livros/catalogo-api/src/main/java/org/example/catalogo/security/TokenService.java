package org.example.catalogo.security;

import java.util.Collection;

public interface TokenService {

    default String generateToken(String username, Collection<String> perfis) {
        return generateToken(username, perfis, null);
    }

    String generateToken(String username, Collection<String> perfis, String nome);

    String validateToken(String token);
}
