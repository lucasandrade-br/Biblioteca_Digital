package org.example.catalogo.controller;

import org.example.catalogo.dto.CreateUsuarioRequest;
import org.example.catalogo.service.UsuarioService;
import org.example.catalogo.service.dto.CoreServiceContracts.CreateUserCommand;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @PostMapping
    public ResponseEntity<Void> criar(@RequestBody @Valid CreateUsuarioRequest request) {
        usuarioService.criar(new CreateUserCommand(request.nome(), request.email(), request.password()));
        return new ResponseEntity<>(HttpStatus.CREATED);
    }
}
