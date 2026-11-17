package org.example.catalogo.controller;

import com.auth0.jwt.JWT;
import com.jayway.jsonpath.JsonPath;
import org.example.catalogo.model.Usuario;
import org.example.catalogo.model.enums.UsuarioPerfil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:catalogo-auth-test;DB_CLOSE_DELAY=-1",
    "token.secret=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
})
@AutoConfigureMockMvc
class AuthIntegrationTest extends IntegrationTestSupport {

    @BeforeEach
    void setUp() {
        cleanDatabase();
    }

    private String registerJson(String email) {
        return "{\"nome\":\"Ana\",\"email\":\"" + email + "\",\"password\":\"senha123\"}";
    }

    @Test
    void publicRegistrationAlwaysCreatesReaderAndEncodesPassword() throws Exception {
        mockMvc.perform(post("/api/usuarios")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Ana\",\"email\":\"ANA@Exemplo.com\",\"password\":\"senha123\",\"perfil\":\"ADMIN\"}"))
            .andExpect(status().isCreated());

        Usuario usuario = usuarioRepository.findByEmailIgnoreCase("ana@exemplo.com").orElseThrow();
        assertEquals("ana@exemplo.com", usuario.getEmail());
        assertEquals(Set.of(UsuarioPerfil.USER), usuario.getPerfis());
        assertNotEquals("senha123", usuario.getSenha());
    }

    @Test
    void rejectsDuplicatedEmailIgnoringCase() throws Exception {
        mockMvc.perform(post("/api/usuarios").contentType(MediaType.APPLICATION_JSON).content(registerJson("ana@exemplo.com")))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/usuarios").contentType(MediaType.APPLICATION_JSON).content(registerJson("ANA@exemplo.com")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("E-mail já cadastrado"));
    }

    @Test
    void validatesRegistrationFields() throws Exception {
        mockMvc.perform(post("/api/usuarios")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"\",\"email\":\"invalido\",\"password\":\"123\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.nome").exists())
            .andExpect(jsonPath("$.errors.email").exists())
            .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void loginReturnsTokenAndWrongPasswordReturns401() throws Exception {
        createUser("Ana Gonçalves", "ana@exemplo.com", UsuarioPerfil.USER);

        var result = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"ana@exemplo.com\",\"password\":\"senha123\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").isString())
            .andReturn();
        String token = JsonPath.read(result.getResponse().getContentAsString(), "$.token");
        assertEquals("Ana Gonçalves", JWT.decode(token).getClaim("nome").asString());

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"ana@exemplo.com\",\"password\":\"errada123\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("E-mail ou senha incorretos"));
    }


    @Test
    void onlyStaffCanSearchExistingReadersAndDeskRegistrationIsUnavailable() throws Exception {
        String staff = createUser("Bibliotecária", "bib@exemplo.com", UsuarioPerfil.LIBRARIAN);
        String reader = createUser("Ana", "ana@exemplo.com", UsuarioPerfil.USER);
        mockMvc.perform(get("/api/balcao/leitores").param("q", "exemplo.com")
                .header("Authorization", bearer(staff)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].email").value("ana@exemplo.com"));
        mockMvc.perform(get("/api/balcao/leitores").param("q", "Ana")
                .header("Authorization", bearer(reader)))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/balcao/leitores").header("Authorization", bearer(staff))
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"nome":"Nova leitora","email":"nova@exemplo.com"}
                    """))
            .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(post("/api/auth/ativar")
                .header("Authorization", bearer(reader)).contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void privateRouteWithoutOrWithInvalidTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/emprestimos")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/emprestimos").header("Authorization", "Bearer inválido"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void readerCannotAccessAdminOrStaffRoutes() throws Exception {
        String readerToken = createUser("Leitor", "leitor@exemplo.com", UsuarioPerfil.USER);

        mockMvc.perform(get("/api/admin/usuarios").header("Authorization", bearer(readerToken)))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/livros/acervo").header("Authorization", bearer(readerToken)))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/balcao/resumo").header("Authorization", bearer(readerToken)))
            .andExpect(status().isForbidden());
    }

    @Test
    void librarianCannotAccessAdminRoutes() throws Exception {
        String librarianToken = createUser("Bibliotecaria", "bib@exemplo.com", UsuarioPerfil.LIBRARIAN);

        mockMvc.perform(get("/api/admin/usuarios").header("Authorization", bearer(librarianToken)))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/balcao/resumo").header("Authorization", bearer(librarianToken)))
            .andExpect(status().isOk());
    }

    @Test
    void adminPromotesUserToLibrarianWithoutNewToken() throws Exception {
        String adminToken = createUser("Admin", "admin@exemplo.com", UsuarioPerfil.ADMIN);
        String userToken = createUser("Futuro", "futuro@exemplo.com", UsuarioPerfil.USER);
        Long userId = usuarioRepository.findByEmailIgnoreCase("futuro@exemplo.com").orElseThrow().getId();

        mockMvc.perform(put("/api/admin/usuarios/" + userId + "/bibliotecario").header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.perfis.length()").value(2));

        mockMvc.perform(get("/api/balcao/resumo").header("Authorization", bearer(userToken)))
            .andExpect(status().isOk());
    }

    @Test
    void inactiveUserLosesAccessAndCannotLogin() throws Exception {
        String adminToken = createUser("Admin", "admin@exemplo.com", UsuarioPerfil.ADMIN);
        String userToken = createUser("Leitor", "leitor@exemplo.com", UsuarioPerfil.USER);
        Long userId = usuarioRepository.findByEmailIgnoreCase("leitor@exemplo.com").orElseThrow().getId();

        mockMvc.perform(patch("/api/admin/usuarios/" + userId + "/inativar").header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ativo").value(false));

        mockMvc.perform(get("/api/emprestimos").header("Authorization", bearer(userToken)))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"leitor@exemplo.com\",\"password\":\"senha123\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").value("Conta inativa. Procure a biblioteca"));
    }

    @Test
    void adminFiltersUsersByNameOrEmailRoleAndStatusBeforePagination() throws Exception {
        String adminToken = createUser("Admin", "admin@exemplo.com", UsuarioPerfil.ADMIN);
        createUser("Ana", "ana@exemplo.com", UsuarioPerfil.USER);
        createUser("Beatriz", "bea@exemplo.com", UsuarioPerfil.LIBRARIAN);
        createUser("Clara", "clara@exemplo.com", UsuarioPerfil.LIBRARIAN);
        Long clara = usuarioRepository.findByEmailIgnoreCase("clara@exemplo.com").orElseThrow().getId();
        mockMvc.perform(patch("/api/admin/usuarios/" + clara + "/inativar").header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/usuarios").header("Authorization", bearer(adminToken))
                .param("q", " EXEMPLO ").param("perfil", "LIBRARIAN").param("ativo", "false").param("size", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].nome").value("Clara"));
    }

    @Test
    void adminCannotInactivateOwnAccount() throws Exception {
        String adminToken = createUser("Admin", "admin@exemplo.com", UsuarioPerfil.ADMIN);
        Long adminId = usuarioRepository.findByEmailIgnoreCase("admin@exemplo.com").orElseThrow().getId();

        mockMvc.perform(patch("/api/admin/usuarios/" + adminId + "/inativar").header("Authorization", bearer(adminToken)))
            .andExpect(status().isConflict());
    }
}
