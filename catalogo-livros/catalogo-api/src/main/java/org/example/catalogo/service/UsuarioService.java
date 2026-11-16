package org.example.catalogo.service;

import org.example.catalogo.service.dto.CoreServiceContracts.CreateUserCommand;
import org.example.catalogo.service.dto.CoreServiceContracts.UserView;
import org.example.catalogo.service.dto.PageQuery;
import org.example.catalogo.service.dto.PageResult;

public interface UsuarioService {

    void criar(CreateUserCommand request);

    PageResult<UserView> listarUsuarios(String term, String role, Boolean active, PageQuery query);

    UserView tornarBibliotecario(Long usuarioId);

    UserView removerBibliotecario(Long usuarioId);

    UserView inativar(Long usuarioId, Long administradorId);

    UserView reativar(Long usuarioId);

    void garantirAdministrador(String email, String password);
}
