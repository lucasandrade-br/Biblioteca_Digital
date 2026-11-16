package org.example.catalogo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AuthorRequest(
    @NotBlank(message = "Informe o nome do autor")
    @Size(max = 150, message = "Nome do autor muito longo")
    String name,
    @Size(max = 80, message = "Nacionalidade muito longa")
    String nationality
) {
}
