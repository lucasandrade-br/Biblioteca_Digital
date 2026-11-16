package org.example.catalogo.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateUsuarioRequest(
    @NotBlank(message = "Informe o nome")
    @Size(max = 255, message = "Nome muito longo")
    String nome,
    @Email(message = "Informe um e-mail válido")
    @NotBlank(message = "Informe o e-mail")
    @Size(max = 255, message = "E-mail muito longo")
    String email,
    @NotBlank(message = "Informe a senha")
    @Size(min = 6, max = 72, message = "A senha deve ter entre 6 e 72 caracteres")
    String password
) {
}
