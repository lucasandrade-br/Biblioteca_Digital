package org.example.catalogo.controller;

import org.example.catalogo.model.enums.UsuarioPerfil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:catalogo-book-test;DB_CLOSE_DELAY=-1",
    "token.secret=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
})
@AutoConfigureMockMvc
class BookIntegrationTest extends IntegrationTestSupport {

    private String staffToken;
    private String readerToken;
    private long genreId;
    private long authorId;

    @BeforeEach
    void setUp() throws Exception {
        cleanDatabase();
        staffToken = createUser("Bibliotecaria", "bib@exemplo.com", UsuarioPerfil.LIBRARIAN);
        readerToken = createUser("Leitor", "leitor@exemplo.com", UsuarioPerfil.USER);
        genreId = createGenre(staffToken, "Romance");
        authorId = createAuthor(staffToken, "Machado de Assis");
    }

    @Test
    void visitorSearchesCatalogByTitleAuthorAndAvailability() throws Exception {
        createBook(staffToken, "Dom Casmurro", VALID_ISBN, genreId, authorId, 2);

        mockMvc.perform(get("/api/livros").param("q", "casmurro"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].titulo").value("Dom Casmurro"))
            .andExpect(jsonPath("$.content[0].availableCopies").value(2));
        mockMvc.perform(get("/api/livros").param("q", "machado"))
            .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/livros").param("q", "inexistente"))
            .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/livros").param("genreId", String.valueOf(genreId)).param("disponiveis", "true"))
            .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void staffFiltersAndPaginatesReferenceListsWhilePublicListsKeepTheirFormat() throws Exception {
        createAuthor(staffToken, "Clarice Lispector");
        createGenre(staffToken, "Fantasia");

        mockMvc.perform(get("/api/acervo/autores").header("Authorization", bearer(staffToken))
                .param("q", "BRASILEIRA").param("size", "1").param("page", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.content[0].name").value("Machado de Assis"));
        mockMvc.perform(get("/api/acervo/generos").header("Authorization", bearer(staffToken))
                .param("q", "rom").param("size", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].name").value("Romance"));
        mockMvc.perform(get("/api/acervo/autores")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/acervo/generos").header("Authorization", bearer(readerToken)))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/autores")).andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(get("/api/generos")).andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void staffCombinesGenreAndBookStatusFilters() throws Exception {
        long bookId = createBook(staffToken, "Dom Casmurro", VALID_ISBN, genreId, authorId, 2);
        long otherGenre = createGenre(staffToken, "Fantasia");
        createBook(staffToken, "Outra obra", OTHER_VALID_ISBN, otherGenre, authorId, 1);
        mockMvc.perform(patch("/api/livros/" + bookId + "/inativar").header("Authorization", bearer(staffToken)))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/livros/acervo").header("Authorization", bearer(staffToken))
                .param("genreId", String.valueOf(genreId)).param("status", "INACTIVE"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].id").value(bookId));
    }

    @Test
    void invalidSortOrIdReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/livros").param("sort", "campoInexistente"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/livros/abc")).andExpect(status().isBadRequest());
    }

    @Test
    void readerAndVisitorCannotManageCollection() throws Exception {
        String body = bookJson("Dom Casmurro", VALID_ISBN, genreId, authorId, 2);

        mockMvc.perform(post("/api/livros").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/livros")
                .header("Authorization", bearer(readerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isForbidden());
    }

    @Test
    void rejectsDuplicatedOrInvalidIsbn() throws Exception {
        createBook(staffToken, "Dom Casmurro", "978-85-359-1484-9", genreId, authorId, 2);

        mockMvc.perform(post("/api/livros")
                .header("Authorization", bearer(staffToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(bookJson("Outra edicao", VALID_ISBN, genreId, authorId, 1)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Já existe um livro cadastrado com este ISBN"));

        mockMvc.perform(post("/api/livros")
                .header("Authorization", bearer(staffToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(bookJson("ISBN errado", "9788535914840", genreId, authorId, 1)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("ISBN inválido. Informe um ISBN-13 válido"));
    }

    @Test
    void validatesRequiredFieldsAndMinimumCopies() throws Exception {
        mockMvc.perform(post("/api/livros")
                .header("Authorization", bearer(staffToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"titulo\":\"\",\"isbn\":\"\",\"authorIds\":[],\"totalCopies\":0}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.titulo").exists())
            .andExpect(jsonPath("$.errors.authorIds").exists())
            .andExpect(jsonPath("$.errors.totalCopies").value("O livro precisa ter ao menos 1 exemplar"));
    }

    @Test
    void storesTituloAndNumeroPaginasAndRejectsInvalidPageCount() throws Exception {
        long bookId = createBook(staffToken, "Dom Casmurro", VALID_ISBN, genreId, authorId, 2);

        mockMvc.perform(get("/api/livros/" + bookId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.titulo").value("Dom Casmurro"))
            .andExpect(jsonPath("$.numeroPaginas").value(200));
        mockMvc.perform(get("/api/livros"))
            .andExpect(jsonPath("$.content[0].numeroPaginas").value(200));

        String zeroPages = bookJson("Sem paginas", OTHER_VALID_ISBN, genreId, authorId, 1)
            .replace("\"numeroPaginas\": 200", "\"numeroPaginas\": 0");
        mockMvc.perform(post("/api/livros")
                .header("Authorization", bearer(staffToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(zeroPages))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.numeroPaginas").value("O livro precisa ter ao menos 1 página"));
    }

    @Test
    void limitsBookToFiveThousandPages() throws Exception {
        String atLimit = bookJson("No limite", VALID_ISBN, genreId, authorId, 1)
            .replace("\"numeroPaginas\": 200", "\"numeroPaginas\": 5000");
        mockMvc.perform(post("/api/livros")
                .header("Authorization", bearer(staffToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(atLimit))
            .andExpect(status().isCreated());

        String overLimit = bookJson("Acima do limite", OTHER_VALID_ISBN, genreId, authorId, 1)
            .replace("\"numeroPaginas\": 200", "\"numeroPaginas\": 5001");
        mockMvc.perform(post("/api/livros")
                .header("Authorization", bearer(staffToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(overLimit))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("O livro pode ter no máximo 5.000 páginas"));
    }

    @Test
    void visitorCannotListCollectionWithInactiveBooks() throws Exception {
        mockMvc.perform(get("/api/livros/acervo")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/livros/acervo").header("Authorization", bearer(readerToken)))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/livros/acervo").header("Authorization", bearer(staffToken)))
            .andExpect(status().isOk());
    }

    @Test
    void inactiveBookDisappearsFromPublicCatalogButStaysInCollection() throws Exception {
        long bookId = createBook(staffToken, "Dom Casmurro", VALID_ISBN, genreId, authorId, 2);

        mockMvc.perform(patch("/api/livros/" + bookId + "/inativar").header("Authorization", bearer(staffToken)))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/livros/" + bookId)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/livros")).andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/livros/acervo/" + bookId).header("Authorization", bearer(staffToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("INACTIVE"));

        mockMvc.perform(patch("/api/livros/" + bookId + "/reativar").header("Authorization", bearer(staffToken)))
            .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/livros/" + bookId)).andExpect(status().isOk());
    }

    @Test
    void updatesBookAndBlocksCopiesBelowLoaned() throws Exception {
        long bookId = createBook(staffToken, "Dom Casmurro", VALID_ISBN, genreId, authorId, 2);
        mockMvc.perform(post("/api/emprestimos")
                .header("Authorization", bearer(readerToken))
                .header("Idempotency-Key", "chave-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookId\":" + bookId + "}"))
            .andExpect(status().isCreated());

        mockMvc.perform(put("/api/livros/" + bookId)
                .header("Authorization", bearer(staffToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(bookJson("Dom Casmurro - revisado", VALID_ISBN, genreId, authorId, 3)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.titulo").value("Dom Casmurro - revisado"))
            .andExpect(jsonPath("$.availableCopies").value(2));

        mockMvc.perform(put("/api/livros/" + bookId)
                .header("Authorization", bearer(staffToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(bookJson("Dom Casmurro", VALID_ISBN, genreId, authorId, 0)))
            .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/livros/" + bookId + "/inativar").header("Authorization", bearer(staffToken)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Livro possui reservas ou empréstimos em aberto"));
    }

    @Test
    void blocksDuplicatedGenreAndDeleteOfLinkedRecords() throws Exception {
        createBook(staffToken, "Dom Casmurro", VALID_ISBN, genreId, authorId, 1);

        mockMvc.perform(post("/api/acervo/generos")
                .header("Authorization", bearer(staffToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"romance\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Gênero já cadastrado"));

        mockMvc.perform(delete("/api/acervo/generos/" + genreId).header("Authorization", bearer(staffToken)))
            .andExpect(status().isConflict());
        mockMvc.perform(delete("/api/acervo/autores/" + authorId).header("Authorization", bearer(staffToken)))
            .andExpect(status().isConflict());

        long unusedGenre = createGenre(staffToken, "Poesia");
        mockMvc.perform(delete("/api/acervo/generos/" + unusedGenre).header("Authorization", bearer(staffToken)))
            .andExpect(status().isNoContent());
    }
}
