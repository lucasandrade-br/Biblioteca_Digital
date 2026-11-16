package org.example.catalogo.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CredentialRequest(
    @Email(message = "Informe um e-mail válido")
    @NotBlank(message = "Informe o e-mail")
    String email,
    @NotBlank(message = "Informe a senha")
    String password
) {
}
