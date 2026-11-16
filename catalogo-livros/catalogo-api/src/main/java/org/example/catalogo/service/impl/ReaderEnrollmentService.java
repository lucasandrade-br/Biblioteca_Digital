package org.example.catalogo.service.impl;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import org.example.catalogo.model.Usuario;
import org.example.catalogo.model.enums.UsuarioPerfil;
import org.example.catalogo.repository.UsuarioRepository;
import org.example.catalogo.service.ReaderEnrollment;
import org.example.catalogo.service.dto.PageQuery;
import org.example.catalogo.service.dto.PageResult;
import org.example.catalogo.service.exception.ApplicationServiceException;
import org.example.catalogo.service.mapper.PageableMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReaderEnrollmentService implements ReaderEnrollment {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration ACTIVATION_WINDOW = Duration.ofDays(7);
    private final UsuarioRepository users;
    private final PasswordEncoder passwords;

    public ReaderEnrollmentService(UsuarioRepository users, PasswordEncoder passwords) {
        this.users = users;
        this.passwords = passwords;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Reader> search(String q, PageQuery query) {
        String term = normalize(q);
        var page = users.searchReaders(term, UsuarioPerfil.USER, PageableMapper.toPageable(query));
        return PageableMapper.toResult(page.map(this::view), query);
    }

    @Override
    @Transactional
    public Enrollment enroll(String name, String email) {
        String normalized = normalize(email);
        var existing = users.findByEmailIgnoreCase(normalized);
        if (existing.isPresent()) {
            var reader = existing.get();
            if (!reader.getPerfis().contains(UsuarioPerfil.USER)) {
                throw new IllegalArgumentException("Este e-mail já está vinculado a uma conta sem perfil de leitor.");
            }
            return new Enrollment(view(reader), null, null);
        }

        Usuario reader = new Usuario(name.trim(), normalized, passwords.encode(randomCode()), UsuarioPerfil.USER);
        Enrollment credential = issue(reader);
        users.saveAndFlush(reader);
        return new Enrollment(view(reader), credential.activationCode(), credential.expiresAt());
    }

    @Override
    @Transactional
    public Enrollment reissue(Long readerId) {
        Usuario reader = users.findWithLockById(readerId).orElseThrow(() ->
            new ApplicationServiceException(ApplicationServiceException.Code.NOT_FOUND, "Leitor não encontrado"));
        if (!reader.isAtivo() || !reader.getPerfis().contains(UsuarioPerfil.USER) || !reader.awaitsActivation()) {
            throw new IllegalArgumentException("Esta conta não está aguardando ativação.");
        }
        return issue(reader);
    }

    @Override
    @Transactional
    public void activate(String email, String code, String password) {
        Usuario reader = users.findWithLockByEmail(normalize(email))
            .orElseThrow(this::invalidCode);
        if (!reader.isAtivo() || !reader.activationIsValidAt(Instant.now())
            || !passwords.matches(code.trim(), reader.getActivationHash())) {
            throw invalidCode();
        }
        if (password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("A senha deve ter até 72 bytes em UTF-8.");
        }
        reader.activateWithPassword(passwords.encode(password));
    }

    private Enrollment issue(Usuario reader) {
        String code = randomCode();
        Instant expiresAt = Instant.now().plus(ACTIVATION_WINDOW);
        reader.issueActivation(passwords.encode(code), expiresAt);
        return new Enrollment(view(reader), code, expiresAt);
    }

    private String randomCode() {
        byte[] bytes = new byte[18];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private Reader view(Usuario reader) {
        return new Reader(reader.getId(), reader.getNome(), reader.getEmail(), reader.isAtivo(), reader.awaitsActivation());
    }

    private IllegalArgumentException invalidCode() {
        return new IllegalArgumentException("Código de ativação inválido ou expirado.");
    }
}
