package org.example.catalogo.service.impl;

import org.example.catalogo.model.Usuario;
import org.example.catalogo.model.enums.LoanStatus;
import org.example.catalogo.model.enums.UsuarioPerfil;
import org.example.catalogo.repository.LoanRepository;
import org.example.catalogo.repository.UsuarioRepository;
import org.example.catalogo.service.UsuarioService;
import org.example.catalogo.service.dto.CoreServiceContracts.CreateUserCommand;
import org.example.catalogo.service.dto.CoreServiceContracts.UserView;
import org.example.catalogo.service.dto.PageQuery;
import org.example.catalogo.service.dto.PageResult;
import org.example.catalogo.service.exception.ApplicationServiceException;
import org.example.catalogo.service.mapper.PageableMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class UsuarioServiceImpl implements UsuarioService {


    private final UsuarioRepository usuarioRepository;
    private final LoanRepository loanRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioServiceImpl(
        UsuarioRepository usuarioRepository,
        LoanRepository loanRepository,
        PasswordEncoder passwordEncoder
    ) {
        this.usuarioRepository = usuarioRepository;
        this.loanRepository = loanRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void criar(CreateUserCommand request) {
        String email = normalizeEmail(request.email());
        usuarioRepository.findByEmailIgnoreCase(email).ifPresent(usuario -> {
            throw new IllegalArgumentException("E-mail já cadastrado");
        });

        Usuario usuario = new Usuario(
            request.name().trim(),
            email,
            passwordEncoder.encode(request.password()),
            UsuarioPerfil.USER
        );

        usuarioRepository.save(usuario);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<UserView> listarUsuarios(String term, String role, Boolean active, PageQuery query) {
        String normalized = term == null ? "" : term.trim().toLowerCase(Locale.ROOT);
        UsuarioPerfil profile = role == null ? null : UsuarioPerfil.valueOf(role);
        var page = usuarioRepository.search(normalized, profile, active, PageableMapper.toPageable(query));
        return PageableMapper.toResult(page.map(this::toView), query);
    }

    @Override
    @Transactional
    public UserView tornarBibliotecario(Long usuarioId) {
        Usuario usuario = findUserWithLock(usuarioId);
        usuario.addPerfil(UsuarioPerfil.LIBRARIAN);
        return toView(usuario);
    }

    @Override
    @Transactional
    public UserView removerBibliotecario(Long usuarioId) {
        Usuario usuario = findUserWithLock(usuarioId);
        usuario.removePerfil(UsuarioPerfil.LIBRARIAN);
        return toView(usuario);
    }

    @Override
    @Transactional
    public UserView inativar(Long usuarioId, Long administradorId) {
        if (usuarioId.equals(administradorId)) {
            throw new IllegalArgumentException("Você não pode inativar a própria conta");
        }
        Usuario usuario = findUserWithLock(usuarioId);
        if (loanRepository.existsByReader_IdAndStatusIn(usuarioId, LoanStatus.OPEN_STATUSES)) {
            throw new IllegalArgumentException("Usuário possui empréstimos em aberto");
        }
        usuario.setAtivo(false);
        return toView(usuario);
    }

    @Override
    @Transactional
    public UserView reativar(Long usuarioId) {
        Usuario usuario = findUserWithLock(usuarioId);
        usuario.setAtivo(true);
        return toView(usuario);
    }

    @Override
    @Transactional
    public void garantirAdministrador(String email, String password) {
        String normalized = normalizeEmail(email);
        var existing = usuarioRepository.findByEmailIgnoreCase(normalized);
        if (existing.isPresent()) {
            if (!existing.get().getPerfis().contains(UsuarioPerfil.ADMIN)) {
                throw new IllegalStateException("ADMIN_EMAIL pertence a uma conta sem perfil ADMIN. Configure outro e-mail.");
            }
            return;
        }

        usuarioRepository.save(new Usuario(
            "Administrador",
            normalized,
            passwordEncoder.encode(password),
            UsuarioPerfil.ADMIN
        ));
    }

    private Usuario findUserWithLock(Long usuarioId) {
        return usuarioRepository.findWithLockById(usuarioId)
            .orElseThrow(() -> new ApplicationServiceException(
                ApplicationServiceException.Code.NOT_FOUND, "Usuário não encontrado"
            ));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private UserView toView(Usuario usuario) {
        return new UserView(
            usuario.getId(),
            usuario.getNome(),
            usuario.getEmail(),
            usuario.isAtivo(),
            usuario.getPerfis().stream().map(Enum::name).collect(Collectors.toSet())
        );
    }
}
