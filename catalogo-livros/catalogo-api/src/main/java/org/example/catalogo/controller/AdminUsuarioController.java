package org.example.catalogo.controller;

import org.example.catalogo.dto.UsuarioResponse;
import org.example.catalogo.model.enums.UsuarioPerfil;
import org.example.catalogo.security.UserSS;
import org.example.catalogo.service.UsuarioService;
import org.example.catalogo.service.dto.CoreServiceContracts.UserView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.stream.Collectors;

import static org.example.catalogo.config.Constants.SECURITY_ROLE_ADMIN;

@RestController
@RequestMapping("/api/admin/usuarios")
@PreAuthorize(SECURITY_ROLE_ADMIN)
public class AdminUsuarioController {

    private final UsuarioService usuarioService;

    public AdminUsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping
    public ResponseEntity<Page<UsuarioResponse>> listar(
        @RequestParam(required = false) String q,
        @RequestParam(required = false) UsuarioPerfil perfil,
        @RequestParam(required = false) Boolean ativo,
        @PageableDefault(size = 20, sort = "nome") Pageable pageable
    ) {
        var result = usuarioService.listarUsuarios(
            q, perfil == null ? null : perfil.name(), ativo, PageHttpMapper.toQuery(pageable)
        );
        return ResponseEntity.ok(PageHttpMapper.toPage(result, AdminUsuarioController::toResponse));
    }

    @PutMapping("/{usuarioId}/bibliotecario")
    public ResponseEntity<UsuarioResponse> tornarBibliotecario(@PathVariable Long usuarioId) {
        return ResponseEntity.ok(toResponse(usuarioService.tornarBibliotecario(usuarioId)));
    }

    @DeleteMapping("/{usuarioId}/bibliotecario")
    public ResponseEntity<UsuarioResponse> removerBibliotecario(@PathVariable Long usuarioId) {
        return ResponseEntity.ok(toResponse(usuarioService.removerBibliotecario(usuarioId)));
    }

    @PatchMapping("/{usuarioId}/inativar")
    public ResponseEntity<UsuarioResponse> inativar(
        @PathVariable Long usuarioId,
        @AuthenticationPrincipal UserSS administrador
    ) {
        return ResponseEntity.ok(toResponse(usuarioService.inativar(usuarioId, administrador.getId())));
    }

    @PatchMapping("/{usuarioId}/reativar")
    public ResponseEntity<UsuarioResponse> reativar(@PathVariable Long usuarioId) {
        return ResponseEntity.ok(toResponse(usuarioService.reativar(usuarioId)));
    }

    private static UsuarioResponse toResponse(UserView user) {
        return new UsuarioResponse(
            user.id(), user.name(), user.email(), user.active(),
            user.roles().stream().map(UsuarioPerfil::valueOf).collect(Collectors.toSet())
        );
    }
}
