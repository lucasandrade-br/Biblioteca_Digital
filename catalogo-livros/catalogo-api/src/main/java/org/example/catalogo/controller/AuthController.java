package org.example.catalogo.controller;

import org.example.catalogo.dto.AuthResponse;
import org.example.catalogo.dto.CredentialRequest;
import org.example.catalogo.security.TokenService;
import org.example.catalogo.security.UserSS;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;

    public AuthController(AuthenticationManager authenticationManager, TokenService tokenService) {
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody @Valid CredentialRequest credentialRequest) {
        var usernamePassword = new UsernamePasswordAuthenticationToken(
            credentialRequest.email(),
            credentialRequest.password()
        );
        var auth = authenticationManager.authenticate(usernamePassword);
        UserSS user = (UserSS) Objects.requireNonNull(auth.getPrincipal());
        var token = tokenService.generateToken(user.getUsername(), user.getPerfis(), user.getNome());

        return ResponseEntity.ok(new AuthResponse(token));
    }
}
