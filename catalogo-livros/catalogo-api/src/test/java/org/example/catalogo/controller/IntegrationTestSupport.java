package org.example.catalogo.controller;

import org.example.catalogo.model.Usuario;
import org.example.catalogo.model.enums.UsuarioPerfil;
import org.example.catalogo.repository.UsuarioRepository;
import org.example.catalogo.security.TokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

abstract class IntegrationTestSupport {

    protected static final String VALID_ISBN = "9788535914849";
    protected static final String OTHER_VALID_ISBN = "9780306406157";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected UsuarioRepository usuarioRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected TokenService tokenService;

    protected void cleanDatabase() {
        jdbcTemplate.execute("DELETE FROM fine_payments");
        jdbcTemplate.execute("DELETE FROM loan_events");
        jdbcTemplate.execute("DELETE FROM loans");
        jdbcTemplate.execute("DELETE FROM book_authors");
        jdbcTemplate.execute("DELETE FROM books");
        jdbcTemplate.execute("DELETE FROM authors");
        jdbcTemplate.execute("DELETE FROM genres");
        jdbcTemplate.execute("DELETE FROM usuario_perfis");
        jdbcTemplate.execute("DELETE FROM usuarios");
    }

    protected String createUser(String name, String email, UsuarioPerfil perfil) {
        Usuario usuario = usuarioRepository.save(new Usuario(name, email, passwordEncoder.encode("senha123"), perfil));
        return tokenService.generateToken(usuario.getEmail(), List.of(perfil.name()));
    }

    protected String bearer(String token) {
        return "Bearer " + token;
    }

    protected long createGenre(String staffToken, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/acervo/generos")
                .header("Authorization", bearer(staffToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\"}"))
            .andExpect(status().isCreated())
            .andReturn();
        return readId(result);
    }

    protected long createAuthor(String staffToken, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/acervo/autores")
                .header("Authorization", bearer(staffToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\",\"nationality\":\"Brasileira\"}"))
            .andExpect(status().isCreated())
            .andReturn();
        return readId(result);
    }

    protected long createBook(String staffToken, String titulo, String isbn, long genreId, long authorId, int copies) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/livros")
                .header("Authorization", bearer(staffToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(bookJson(titulo, isbn, genreId, authorId, copies)))
            .andExpect(status().isCreated())
            .andReturn();
        return readId(result);
    }

    protected String bookJson(String titulo, String isbn, long genreId, long authorId, int copies) {
        return """
            {
              "titulo": "%s",
              "isbn": "%s",
              "publisher": "Editora Teste",
              "publicationYear": 2001,
              "numeroPaginas": 200,
              "synopsis": "Sinopse de teste.",
              "genreId": %d,
              "authorIds": [%d],
              "totalCopies": %d
            }
            """.formatted(titulo, isbn, genreId, authorId, copies);
    }

    protected long readId(MvcResult result) throws Exception {
        Matcher matcher = Pattern.compile("\"id\":(\\d+)").matcher(result.getResponse().getContentAsString());
        if (!matcher.find()) {
            throw new IllegalStateException("Resposta sem id: " + result.getResponse().getContentAsString());
        }
        return Long.parseLong(matcher.group(1));
    }

    protected String readField(MvcResult result, String field) throws Exception {
        Matcher matcher = Pattern.compile("\"" + field + "\":\"([^\"]+)\"").matcher(result.getResponse().getContentAsString());
        if (!matcher.find()) {
            throw new IllegalStateException("Resposta sem " + field + ": " + result.getResponse().getContentAsString());
        }
        return matcher.group(1);
    }
}
