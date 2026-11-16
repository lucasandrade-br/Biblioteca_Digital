package org.example.catalogo.service.impl;

import org.example.catalogo.model.Usuario;
import org.example.catalogo.model.enums.UsuarioPerfil;
import org.example.catalogo.repository.LoanRepository;
import org.example.catalogo.repository.UsuarioRepository;
import org.example.catalogo.service.dto.CoreServiceContracts.CreateUserCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UsuarioServiceImplTest {

    private UsuarioRepository usuarioRepository;
    private LoanRepository loanRepository;
    private PasswordEncoder passwordEncoder;
    private UsuarioServiceImpl usuarioService;

    @BeforeEach
    void setUp() {
        usuarioRepository = mock(UsuarioRepository.class);
        loanRepository = mock(LoanRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        usuarioService = new UsuarioServiceImpl(usuarioRepository, loanRepository, passwordEncoder);
    }

    @Test
    void createsReaderWithNormalizedEmailAndEncodedPassword() {
        when(usuarioRepository.findByEmailIgnoreCase("ana@exemplo.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("senha123")).thenReturn("hash");

        usuarioService.criar(new CreateUserCommand(" Ana ", " ANA@exemplo.com ", "senha123"));

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        assertEquals("Ana", captor.getValue().getNome());
        assertEquals("ana@exemplo.com", captor.getValue().getEmail());
        assertEquals("hash", captor.getValue().getSenha());
        assertEquals(Set.of(UsuarioPerfil.USER), captor.getValue().getPerfis());
    }

    @Test
    void doesNotCreateUserWithRepeatedEmail() {
        when(usuarioRepository.findByEmailIgnoreCase("ana@exemplo.com"))
            .thenReturn(Optional.of(new Usuario("Ana", "ana@exemplo.com", "hash", UsuarioPerfil.USER)));

        assertThrows(IllegalArgumentException.class,
            () -> usuarioService.criar(new CreateUserCommand("Ana", "ana@exemplo.com", "senha123")));

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void doesNotDeactivateUserWithOpenLoans() {
        Usuario usuario = new Usuario("Ana", "ana@exemplo.com", "hash", UsuarioPerfil.USER);
        when(usuarioRepository.findWithLockById(2L)).thenReturn(Optional.of(usuario));
        when(loanRepository.existsByReader_IdAndStatusIn(any(), any())).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> usuarioService.inativar(2L, 1L));
    }

    @Test
    void createsInitialAdministratorWhenEmailIsAvailable() {
        when(usuarioRepository.findByEmailIgnoreCase("admin@exemplo.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("senha-inicial")).thenReturn("hash-inicial");

        usuarioService.garantirAdministrador(" ADMIN@exemplo.com ", "senha-inicial");

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        assertEquals("admin@exemplo.com", captor.getValue().getEmail());
        assertEquals("hash-inicial", captor.getValue().getSenha());
        assertEquals(Set.of(UsuarioPerfil.ADMIN), captor.getValue().getPerfis());
    }

    @Test
    void keepsExistingAdministratorCredentialsOnRestart() {
        Usuario admin = new Usuario("Admin", "admin@exemplo.com", "hash-original", UsuarioPerfil.ADMIN);
        when(usuarioRepository.findByEmailIgnoreCase("admin@exemplo.com")).thenReturn(Optional.of(admin));

        usuarioService.garantirAdministrador("admin@exemplo.com", "outra-senha");

        assertEquals(Set.of(UsuarioPerfil.ADMIN), admin.getPerfis());
        assertEquals("hash-original", admin.getSenha());
        verify(passwordEncoder, never()).encode(any());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void refusesToPromoteAnExistingReaderDuringBootstrap() {
        Usuario leitor = new Usuario("Leitor", "leitor@exemplo.com", "hash-original", UsuarioPerfil.USER);
        when(usuarioRepository.findByEmailIgnoreCase("leitor@exemplo.com")).thenReturn(Optional.of(leitor));

        var error = assertThrows(IllegalStateException.class,
            () -> usuarioService.garantirAdministrador(" LEITOR@exemplo.com ", "senha-inicial"));

        assertEquals("ADMIN_EMAIL pertence a uma conta sem perfil ADMIN. Configure outro e-mail.", error.getMessage());
        assertEquals(Set.of(UsuarioPerfil.USER), leitor.getPerfis());
        assertEquals("hash-original", leitor.getSenha());
        verify(passwordEncoder, never()).encode(any());
        verify(usuarioRepository, never()).save(any());
    }
}
