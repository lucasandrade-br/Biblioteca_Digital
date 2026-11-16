package org.example.catalogo.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BookRequest(
    @NotBlank(message = "Informe o título")
    @Size(max = 200, message = "Título muito longo")
    String titulo,
    @NotBlank(message = "Informe o ISBN")
    @Size(max = 20, message = "ISBN muito longo")
    String isbn,
    @NotBlank(message = "Informe a editora")
    @Size(max = 120, message = "Nome da editora muito longo")
    String publisher,
    @NotNull(message = "Informe o ano de publicação")
    Integer publicationYear,
    @NotNull(message = "Informe o número de páginas")
    @Min(value = 1, message = "O livro precisa ter ao menos 1 página")
    Integer numeroPaginas,
    @NotBlank(message = "Informe a sinopse")
    @Size(max = 2000, message = "A sinopse deve ter no máximo 2000 caracteres")
    String synopsis,
    @Size(max = 500, message = "URL da capa muito longa")
    String coverUrl,
    @NotNull(message = "Selecione o gênero")
    Long genreId,
    @NotEmpty(message = "Selecione ao menos um autor")
    List<@NotNull Long> authorIds,
    @NotNull(message = "Informe a quantidade de exemplares")
    @Min(value = 1, message = "O livro precisa ter ao menos 1 exemplar")
    @Max(value = 500, message = "Limite de 500 exemplares por livro")
    Integer totalCopies
) {
}
