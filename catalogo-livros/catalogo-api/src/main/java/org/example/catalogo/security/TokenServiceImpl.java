package org.example.catalogo.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

@Service
public class TokenServiceImpl implements TokenService {

    private static final String ISSUER = "catalogo-api";
    private static final Duration EXPIRATION = Duration.ofHours(2);

    @Value("${token.secret}")
    private String secret;

    @PostConstruct
    void validateSecret() {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 64) {
            throw new IllegalStateException("TOKEN_SECRET deve conter pelo menos 64 bytes UTF-8");
        }
    }

    @Override
    public String generateToken(String username, Collection<String> perfis, String nome) {
        try {
            Algorithm algorithm = Algorithm.HMAC512(secret);
            var token = JWT.create()
                .withIssuer(ISSUER)
                .withSubject(username)
                .withClaim("perfis", List.copyOf(perfis))
                .withExpiresAt(Instant.now().plus(EXPIRATION));
            if (nome != null) {
                token.withClaim("nome", nome);
            }
            return token.sign(algorithm);
        } catch (JWTCreationException exception) {
            throw new IllegalStateException("Erro ao gerar token", exception);
        }
    }

    @Override
    public String validateToken(String token) {
        try {
            Algorithm algorithm = Algorithm.HMAC512(secret);
            return JWT.require(algorithm)
                .withIssuer(ISSUER)
                .build()
                .verify(token)
                .getSubject();
        } catch (JWTVerificationException exception) {
            return null;
        }
    }
}
