package org.example.catalogo.controller;

import org.example.catalogo.dto.AuthResponse;
import org.example.catalogo.dto.CredentialRequest;
import org.example.catalogo.service.ReaderEnrollment;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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

    private final ReaderEnrollment enrollment;

    public AuthController(AuthenticationManager authenticationManager, TokenService tokenService, ReaderEnrollment enrollment) {
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
        this.enrollment = enrollment;
    }

    public record ActivationRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(min = 6, max = 72) String password
    ) {}

    @PostMapping("/ativar")
    public ResponseEntity<Void> activate(@RequestBody @Valid ActivationRequest request) {
        enrollment.activate(request.email(), request.code(), request.password());
        return ResponseEntity.noContent().build();
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
