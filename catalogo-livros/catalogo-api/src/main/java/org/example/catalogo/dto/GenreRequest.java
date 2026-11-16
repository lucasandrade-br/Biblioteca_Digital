package org.example.catalogo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GenreRequest(
    @NotBlank(message = "Informe o nome do gênero")
    @Size(max = 80, message = "Nome do gênero muito longo")
    String name
) {
}
