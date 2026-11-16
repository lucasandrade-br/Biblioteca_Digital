package org.example.catalogo.security;

import com.auth0.jwt.JWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TokenServiceTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private TokenServiceImpl tokenService;

    @BeforeEach
    void setUp() {
        tokenService = new TokenServiceImpl();
        ReflectionTestUtils.setField(tokenService, "secret", SECRET);
    }

    @Test
    void generatesTokenWithSubjectAndProfiles() {
        String token = tokenService.generateToken("ana@exemplo.com", List.of("USER"));

        assertEquals("ana@exemplo.com", tokenService.validateToken(token));
        assertEquals(List.of("USER"), JWT.decode(token).getClaim("perfis").asList(String.class));
    }

    @Test
    void rejectsTamperedToken() {
        String token = tokenService.generateToken("ana@exemplo.com", List.of("USER"));

        assertNull(tokenService.validateToken(token + "x"));
    }

    @Test
    void rejectsTokenSignedWithAnotherSecret() {
        TokenServiceImpl other = new TokenServiceImpl();
        ReflectionTestUtils.setField(other, "secret", SECRET.replace('0', '1'));

        assertNull(tokenService.validateToken(other.generateToken("ana@exemplo.com", List.of("ADMIN"))));
    }

    @Test
    void refusesShortSecret() {
        TokenServiceImpl weak = new TokenServiceImpl();
        ReflectionTestUtils.setField(weak, "secret", "curto");

        assertThrows(IllegalStateException.class, weak::validateSecret);
    }
}
