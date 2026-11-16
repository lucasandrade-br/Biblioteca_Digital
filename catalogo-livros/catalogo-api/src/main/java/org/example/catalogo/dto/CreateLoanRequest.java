package org.example.catalogo.dto;

import jakarta.validation.constraints.NotNull;

public record CreateLoanRequest(
    @NotNull(message = "Informe o livro")
    Long bookId
) {
}
