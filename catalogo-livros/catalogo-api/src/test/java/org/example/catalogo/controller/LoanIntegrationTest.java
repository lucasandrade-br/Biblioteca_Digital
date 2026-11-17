package org.example.catalogo.controller;

import org.example.catalogo.model.enums.PaymentOutcome;
import org.example.catalogo.model.enums.UsuarioPerfil;
import org.example.catalogo.service.LoanService;
import org.example.catalogo.service.UsuarioService;
import org.example.catalogo.service.exception.ApplicationServiceException;
import org.example.catalogo.service.expiration.ReservationExpiration;
import org.example.catalogo.service.impl.MockPaymentGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:catalogo-loan-test;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
    "token.secret=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
    "catalogo.loans.expiration-scan-ms=3600000"
})
@AutoConfigureMockMvc
class LoanIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockPaymentGateway paymentGateway;

    @Autowired
    private LoanService loanService;

    @Autowired
    private ReservationExpiration expiration;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private String staffToken;
    private String readerToken;
    private String otherReaderToken;
    private long genreId;
    private long authorId;
    private long bookId;

    @BeforeEach
    void setUp() throws Exception {
        cleanDatabase();
        staffToken = createUser("Bibliotecaria", "bib@exemplo.com", UsuarioPerfil.LIBRARIAN);
        readerToken = createUser("Leitor", "leitor@exemplo.com", UsuarioPerfil.USER);
        otherReaderToken = createUser("Outra leitora", "outra@exemplo.com", UsuarioPerfil.USER);
        genreId = createGenre(staffToken, "Romance");
        authorId = createAuthor(staffToken, "Machado de Assis");
        bookId = createBook(staffToken, "Dom Casmurro", VALID_ISBN, genreId, authorId, 2);
        paymentGateway.setNextOutcomeForTest(null);
    }

    private MvcResult reserve(String token, String key, long book, ResultMatcher expected) throws Exception {
        return mockMvc.perform(post("/api/emprestimos")
                .header("Authorization", bearer(token))
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookId\":" + book + "}"))
            .andExpect(expected)
            .andReturn();
    }

    private MvcResult pickup(String code, ResultMatcher expected) throws Exception {
        return mockMvc.perform(post("/api/balcao/retiradas")
                .header("Authorization", bearer(staffToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pickupCode\":\"" + code + "\"}"))
            .andExpect(expected)
            .andReturn();
    }

    private long reserveAndPickup(String token, String key, long book) throws Exception {
        MvcResult reserved = reserve(token, key, book, status().isCreated());
        pickup(readField(reserved, "pickupCode"), status().isOk());
        return readId(reserved);
    }

    private int availableCopies(long book) {
        return jdbcTemplate.queryForObject(
            "SELECT total_copies - loaned_copies FROM books WHERE id = ?", Integer.class, book
        );
    }

    private void moveDueDate(long loanId, Duration fromNow) {
        jdbcTemplate.update("UPDATE loans SET due_at = ? WHERE id = ?", Timestamp.from(Instant.now().plus(fromNow)), loanId);
    }

    @Test
    void loanSearchRespectsReaderScopeAndDeskFilters() throws Exception {
        reserve(readerToken, "filtro-1", bookId, status().isCreated());
        reserve(otherReaderToken, "filtro-2", bookId, status().isCreated());

        mockMvc.perform(get("/api/emprestimos").header("Authorization", bearer(readerToken))
                .param("q", "CASMURRO").param("status", "RESERVED").param("size", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].readerEmail").value("leitor@exemplo.com"));
        mockMvc.perform(get("/api/emprestimos").header("Authorization", bearer(readerToken))
                .param("q", "casmurro").param("status", "RETURNED"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/balcao/emprestimos").header("Authorization", bearer(staffToken))
                .param("q", "OUTRA@EXEMPLO.COM").param("status", "RESERVED"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].readerEmail").value("outra@exemplo.com"));
    }


    @Test
    void deskCanConvertAnExistingReservationWithoutChargingStockTwice() throws Exception {
        var reserved = reserve(readerToken, "assistida", bookId, status().isCreated());
        long id = readId(reserved);
        String code = readField(reserved, "pickupCode");
        String readerId = usuarioRepository.findByEmailIgnoreCase("leitor@exemplo.com").orElseThrow().getId().toString();
        mockMvc.perform(get("/api/balcao/emprestimos/preparar").header("Authorization", bearer(staffToken))
                .param("readerId", readerId).param("bookId", Long.toString(bookId)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.reservation.id").value(id));
        mockMvc.perform(post("/api/balcao/emprestimos/" + id + "/retirada-assistida")
                .header("Authorization", bearer(readerToken)))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/balcao/emprestimos/" + id + "/retirada-assistida")
                .header("Authorization", bearer(staffToken)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
        pickup(code, status().isConflict());
        assertEquals(1, availableCopies(bookId));
        mockMvc.perform(post("/api/balcao/emprestimos/" + id + "/devolucao")
                .header("Authorization", bearer(staffToken)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RETURNED"));
        assertEquals(2, availableCopies(bookId));
    }

    @Test
    void deskRegistersAnIdempotentDirectLoanForAnExistingReader() throws Exception {
        Long readerId = usuarioRepository.findByEmailIgnoreCase("leitor@exemplo.com").orElseThrow().getId();
        String key = "atendimento-direto";
        var loan = lendAtDesk(readerId, bookId, key, status().isOk());
        long id = readId(loan);
        assertEquals(14, Duration.between(
            Instant.parse(readField(loan, "pickedUpAt")), Instant.parse(readField(loan, "dueAt"))
        ).toDays());
        assertEquals(id, readId(lendAtDesk(readerId, bookId, key, status().isOk())));
        lendAtDesk(readerId, bookId, "atendimento-duplicado", status().isConflict());
        assertEquals(1, availableCopies(bookId));
        moveDueDate(id, Duration.ofHours(-2));
        mockMvc.perform(post("/api/balcao/emprestimos/" + id + "/devolucao")
                .header("Authorization", bearer(staffToken)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.fineInCents").value(200))
            .andExpect(jsonPath("$.events[0].actorName").value("Bibliotecaria"));
        assertEquals(2, availableCopies(bookId));

        mockMvc.perform(get("/api/emprestimos").param("scope", "HISTORICO")
                .header("Authorization", bearer(readerToken)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(id));
        lendAtDesk(readerId, bookId, "atendimento-multa", status().isConflict());
    }

    @Test
    void deskProtectsTheLastCopyAndFiltersDeadlinesBeforePaging() throws Exception {
        long scarceBook = createBook(staffToken, "Exemplar único", OTHER_VALID_ISBN, genreId, authorId, 1);
        Long readerId = usuarioRepository.findByEmailIgnoreCase("leitor@exemplo.com").orElseThrow().getId();
        Long otherId = usuarioRepository.findByEmailIgnoreCase("outra@exemplo.com").orElseThrow().getId();
        long lateId = readId(lendAtDesk(readerId, scarceBook, "ultimo-1", status().isOk()));
        lendAtDesk(otherId, scarceBook, "ultimo-2", status().isConflict());
        long dueId = readId(lendAtDesk(otherId, bookId, "hoje-1", status().isOk()));
        moveDueDate(lateId, Duration.ofDays(-1));
        var zone = ZoneId.of("America/Sao_Paulo");
        LocalDate today = LocalDate.now(zone);
        var todayEnd = today.plusDays(1).atStartOfDay(zone).toInstant();
        jdbcTemplate.update("UPDATE loans SET due_at = ? WHERE id = ?", Timestamp.from(todayEnd.minusSeconds(1)), dueId);

        mockMvc.perform(get("/api/balcao/emprestimos").header("Authorization", bearer(staffToken))
                .param("pending", "true").param("size", "1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(lateId))
            .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get("/api/balcao/emprestimos").header("Authorization", bearer(staffToken))
                .param("deadline", "HOJE").param("status", "ACTIVE").param("q", "outra"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(dueId));
        jdbcTemplate.update("UPDATE loans SET due_at = ? WHERE id = ?", Timestamp.from(todayEnd), dueId);
        mockMvc.perform(get("/api/balcao/emprestimos").header("Authorization", bearer(staffToken))
                .param("from", today.toString()).param("to", today.toString()).param("q", "outra"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }


    @Test
    void concurrentDeskLoansCompeteForOnlyOneAvailableCopy() throws Exception {
        long scarce = createBook(staffToken, "Último exemplar", OTHER_VALID_ISBN, genreId, authorId, 1);
        Long first = usuarioRepository.findByEmailIgnoreCase("leitor@exemplo.com").orElseThrow().getId();
        Long second = usuarioRepository.findByEmailIgnoreCase("outra@exemplo.com").orElseThrow().getId();
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var firstRequest = executor.submit(() -> {
                start.await();
                return lendAtDesk(first, scarce, "concorrente-1", result -> {}).getResponse().getStatus();
            });
            var secondRequest = executor.submit(() -> {
                start.await();
                return lendAtDesk(second, scarce, "concorrente-2", result -> {}).getResponse().getStatus();
            });
            start.countDown();
            var statuses = List.of(firstRequest.get(10, TimeUnit.SECONDS), secondRequest.get(10, TimeUnit.SECONDS));
            assertTrue(statuses.containsAll(List.of(200, 409)));
        }
        assertEquals(0, availableCopies(scarce));
    }

    private MvcResult lendAtDesk(Long readerId, long bookId, String key, ResultMatcher expected) throws Exception {
        return mockMvc.perform(post("/api/balcao/emprestimos").header("Authorization", bearer(staffToken))
                .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"readerId":%d,"bookId":%d}
                    """.formatted(readerId, bookId)))
            .andExpect(expected).andReturn();
    }

    @Test
    void completesMainFlowFromReservationToReturn() throws Exception {
        mockMvc.perform(post("/api/usuarios")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"nome":"Nova leitora","email":"nova@exemplo.com","password":"senha123"}
                    """))
            .andExpect(status().isCreated());
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"nova@exemplo.com","password":"senha123"}
                    """))
            .andExpect(status().isOk())
            .andReturn();
        String readerToken = readField(login, "token");
        MvcResult reserved = reserve(readerToken, "fluxo-1", bookId, status().isCreated());
        long loanId = readId(reserved);
        String code = readField(reserved, "pickupCode");
        assertEquals(1, availableCopies(bookId));

        mockMvc.perform(get("/api/balcao/retiradas/" + code).header("Authorization", bearer(staffToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.readerName").value("Nova leitora"))
            .andExpect(jsonPath("$.canPickup").value(true))
            .andExpect(jsonPath("$.status").value("RESERVED"));

        pickup(code, status().isOk());
        assertEquals(1, availableCopies(bookId));

        mockMvc.perform(get("/api/emprestimos/" + loanId).header("Authorization", bearer(readerToken)))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.canRenew").value(true))
            .andExpect(jsonPath("$.pickupCode").doesNotExist())
            .andExpect(jsonPath("$.dueAt").isString());

        mockMvc.perform(post("/api/balcao/emprestimos/" + loanId + "/devolucao").header("Authorization", bearer(staffToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("RETURNED"))
            .andExpect(jsonPath("$.fineStatus").value("NONE"));
        assertEquals(2, availableCopies(bookId));

        mockMvc.perform(get("/api/emprestimos").header("Authorization", bearer(readerToken)))
            .andExpect(jsonPath("$.content[0].events.length()").value(3))
            .andExpect(jsonPath("$.content[0].events[0].type").value("RESERVED"))
            .andExpect(jsonPath("$.content[0].events[2].type").value("RETURNED"));
    }

    @Test
    void replaysSameIdempotencyKeyWithoutReservingTwice() throws Exception {
        long first = readId(reserve(readerToken, "mesma-chave", bookId, status().isCreated()));
        long second = readId(reserve(readerToken, "mesma-chave", bookId, status().isCreated()));

        assertEquals(first, second);
        assertEquals(1, availableCopies(bookId));

        long otherBook = createBook(staffToken, "Iracema", OTHER_VALID_ISBN, genreId, authorId, 1);
        reserve(readerToken, "mesma-chave", otherBook, status().isConflict());
    }

    @Test
    void requiresIdempotencyHeader() throws Exception {
        mockMvc.perform(post("/api/emprestimos")
                .header("Authorization", bearer(readerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookId\":" + bookId + "}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void blocksDuplicatedReservationOfSameBook() throws Exception {
        reserve(readerToken, "duplicada-1", bookId, status().isCreated());

        mockMvc.perform(post("/api/emprestimos")
                .header("Authorization", bearer(readerToken))
                .header("Idempotency-Key", "duplicada-2")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookId\":" + bookId + "}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Você já possui uma reserva ou empréstimo deste livro"));
    }

    @Test
    void blocksWhenThereIsNoAvailableCopy() throws Exception {
        long lastCopy = createBook(staffToken, "Iracema", OTHER_VALID_ISBN, genreId, authorId, 1);
        reserve(readerToken, "ultima-1", lastCopy, status().isCreated());

        mockMvc.perform(post("/api/emprestimos")
                .header("Authorization", bearer(otherReaderToken))
                .header("Idempotency-Key", "ultima-2")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookId\":" + lastCopy + "}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Não há exemplares disponíveis deste livro"));
    }

    @Test
    void concurrentReadersCannotTakeTheSameLastCopy() throws Exception {
        long lastCopy = createBook(staffToken, "Iracema", OTHER_VALID_ISBN, genreId, authorId, 1);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> results = List.of(readerToken, otherReaderToken).stream()
                .map(token -> executor.submit(() -> {
                    start.await();
                    return mockMvc.perform(post("/api/emprestimos")
                            .header("Authorization", bearer(token))
                            .header("Idempotency-Key", "concorrente-" + token.hashCode())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"bookId\":" + lastCopy + "}"))
                        .andReturn().getResponse().getStatus();
                }))
                .toList();
            start.countDown();

            int created = 0;
            for (Future<Integer> result : results) {
                created += result.get() == 201 ? 1 : 0;
            }
            assertEquals(1, created);
            assertEquals(0, availableCopies(lastCopy));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void committedReservationPreventsConcurrentReaderDeactivation() {
        long readerId = usuarioRepository.findByEmailIgnoreCase("leitor@exemplo.com").orElseThrow().getId();
        long administratorId = createAdministrator();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> deactivation = competeForReaderLock(executor,
                () -> loanService.reserve(bookId, "reserva-primeiro", readerId),
                () -> usuarioService.inativar(readerId, administratorId));

            var failure = assertThrows(ExecutionException.class, () -> deactivation.get(5, TimeUnit.SECONDS));
            var error = assertInstanceOf(IllegalArgumentException.class, failure.getCause());
            assertEquals("Usuário possui empréstimos em aberto", error.getMessage());
            assertTrue(usuarioRepository.findById(readerId).orElseThrow().isAtivo());
            assertEquals(1, availableCopies(bookId));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void committedDeactivationPreventsConcurrentReservationWithPreviouslyAuthenticatedReader() {
        long readerId = usuarioRepository.findByEmailIgnoreCase("leitor@exemplo.com").orElseThrow().getId();
        long administratorId = createAdministrator();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> reservation = competeForReaderLock(executor,
                () -> usuarioService.inativar(readerId, administratorId),
                () -> loanService.reserve(bookId, "inativacao-primeiro", readerId));

            var failure = assertThrows(ExecutionException.class, () -> reservation.get(5, TimeUnit.SECONDS));
            var error = assertInstanceOf(ApplicationServiceException.class, failure.getCause());
            assertEquals(ApplicationServiceException.Code.UNAUTHORIZED, error.getCode());
            assertFalse(usuarioRepository.findById(readerId).orElseThrow().isAtivo());
            assertEquals(2, availableCopies(bookId));
        } finally {
            executor.shutdownNow();
        }
    }

    private long createAdministrator() {
        String email = "admin@exemplo.com";
        createUser("Admin", email, UsuarioPerfil.ADMIN);
        return usuarioRepository.findByEmailIgnoreCase(email).orElseThrow().getId();
    }

    private Future<?> competeForReaderLock(ExecutorService executor, Runnable first, Runnable second) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            first.run();
            Future<?> waiting = executor.submit(second);
            // Confirma a espera no H2 antes de liberar o bloqueio no commit.
            await().pollInSameThread().atMost(Duration.ofSeconds(5)).until(() -> Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.SESSIONS WHERE BLOCKER_ID = SESSION_ID())",
                Boolean.class
            )));
            return waiting;
        });
    }

    @Test
    void limitsOpenLoansPerReader() throws Exception {
        for (int i = 0; i < 3; i++) {
            long book = createBook(staffToken, "Livro " + i, isbnFor(i), genreId, authorId, 1);
            reserve(readerToken, "limite-" + i, book, status().isCreated());
        }

        mockMvc.perform(post("/api/emprestimos")
                .header("Authorization", bearer(readerToken))
                .header("Idempotency-Key", "limite-extra")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookId\":" + bookId + "}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Limite de 3 livros simultâneos atingido"));
    }

    @Test
    void blocksReservationOfInactiveBook() throws Exception {
        mockMvc.perform(patch("/api/livros/" + bookId + "/inativar").header("Authorization", bearer(staffToken)))
            .andExpect(status().isNoContent());

        reserve(readerToken, "inativo", bookId, status().isConflict());
    }

    @Test
    void adminWithoutReaderProfileCannotReserve() throws Exception {
        String adminToken = createUser("Admin", "admin@exemplo.com", UsuarioPerfil.ADMIN);

        reserve(adminToken, "admin", bookId, status().isForbidden());
    }

    @Test
    void readerCancelsOnlyOwnPendingReservationAndCopyIsReleased() throws Exception {
        long loanId = readId(reserve(readerToken, "cancelar", bookId, status().isCreated()));
        assertEquals(1, availableCopies(bookId));

        mockMvc.perform(patch("/api/emprestimos/" + loanId + "/cancelar").header("Authorization", bearer(otherReaderToken)))
            .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/emprestimos/" + loanId + "/cancelar").header("Authorization", bearer(readerToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertEquals(2, availableCopies(bookId));

        mockMvc.perform(patch("/api/emprestimos/" + loanId + "/cancelar").header("Authorization", bearer(readerToken)))
            .andExpect(status().isOk());
        assertEquals(2, availableCopies(bookId));
    }

    @Test
    void cannotCancelAfterPickup() throws Exception {
        long loanId = reserveAndPickup(readerToken, "retirado", bookId);

        mockMvc.perform(patch("/api/emprestimos/" + loanId + "/cancelar").header("Authorization", bearer(readerToken)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Somente reservas ainda não retiradas podem ser canceladas"));
    }

    @Test
    void readerCannotSeeLoanOfAnotherReader() throws Exception {
        long loanId = readId(reserve(readerToken, "privado", bookId, status().isCreated()));

        mockMvc.perform(get("/api/emprestimos/" + loanId).header("Authorization", bearer(otherReaderToken)))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/emprestimos").header("Authorization", bearer(otherReaderToken)))
            .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void readerCannotOperateDesk() throws Exception {
        MvcResult reserved = reserve(readerToken, "balcao", bookId, status().isCreated());

        mockMvc.perform(post("/api/balcao/retiradas")
                .header("Authorization", bearer(readerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pickupCode\":\"" + readField(reserved, "pickupCode") + "\"}"))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/balcao/emprestimos/" + readId(reserved) + "/devolucao")
                .header("Authorization", bearer(readerToken)))
            .andExpect(status().isForbidden());
    }

    @Test
    void pickupCodeWorksOnlyOnce() throws Exception {
        MvcResult reserved = reserve(readerToken, "uso-unico", bookId, status().isCreated());
        String code = readField(reserved, "pickupCode");

        pickup(code, status().isOk());
        pickup(code, status().isConflict());
        pickup("LIV-INEXISTE", status().isNotFound());
    }

    @Test
    void expiredReservationReleasesCopyAndCannotBePickedUp() throws Exception {
        MvcResult reserved = reserve(readerToken, "expira", bookId, status().isCreated());
        long loanId = readId(reserved);
        String code = readField(reserved, "pickupCode");
        movePickupDeadlineToPast(loanId);

        mockMvc.perform(get("/api/emprestimos/" + loanId).header("Authorization", bearer(readerToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("RESERVED"))
            .andExpect(jsonPath("$.canPickup").value(false))
            .andExpect(jsonPath("$.canCancel").value(false))
            .andExpect(jsonPath("$.pickupCode").doesNotExist());
        mockMvc.perform(get("/api/emprestimos").header("Authorization", bearer(readerToken)))
            .andExpect(jsonPath("$.content[0].canPickup").value(false));
        mockMvc.perform(get("/api/balcao/retiradas/" + code).header("Authorization", bearer(staffToken)))
            .andExpect(jsonPath("$.canPickup").value(false));
        mockMvc.perform(get("/api/balcao/emprestimos").header("Authorization", bearer(staffToken)))
            .andExpect(jsonPath("$.content[0].canPickup").value(false));
        assertEquals(1, availableCopies(bookId));

        pickup(code, status().isConflict());

        assertEquals("RESERVED", jdbcTemplate.queryForObject("SELECT status FROM loans WHERE id = ?", String.class, loanId));
        expiration.expireScheduledLoans();
        expiration.expireScheduledLoans();

        assertEquals(2, availableCopies(bookId));
        mockMvc.perform(get("/api/emprestimos/" + loanId).header("Authorization", bearer(readerToken)))
            .andExpect(jsonPath("$.status").value("EXPIRED"))
            .andExpect(jsonPath("$.events.length()").value(2))
            .andExpect(jsonPath("$.events[1].type").value("EXPIRED"));
    }

    @Test
    void newReservationReclaimsLastCopyBeforeScheduledScan() throws Exception {
        long lastCopy = createBook(staffToken, "Iracema", OTHER_VALID_ISBN, genreId, authorId, 1);
        MvcResult abandoned = reserve(readerToken, "ultima-abandonada", lastCopy, status().isCreated());
        long abandonedId = readId(abandoned);
        movePickupDeadlineToPast(abandonedId);
        assertEquals(0, availableCopies(lastCopy));

        reserve(otherReaderToken, "ultima-liberada", lastCopy, status().isCreated());
        expiration.expireScheduledLoans();

        assertEquals(0, availableCopies(lastCopy));
        assertEquals("EXPIRED",
            jdbcTemplate.queryForObject("SELECT status FROM loans WHERE id = ?", String.class, abandonedId));
        assertEquals(1, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM loan_events WHERE loan_id = ? AND type = 'EXPIRED'", Integer.class, abandonedId));
    }

    private void movePickupDeadlineToPast(long loanId) {
        jdbcTemplate.update("UPDATE loans SET pickup_deadline = ? WHERE id = ?",
            Timestamp.from(Instant.now().minusSeconds(60)), loanId);
    }

    @Test
    void renewsUntilLimitExtendingDueDate() throws Exception {
        long loanId = reserveAndPickup(readerToken, "renova", bookId);

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(patch("/api/emprestimos/" + loanId + "/renovar").header("Authorization", bearer(readerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxRenewals").value(2))
                .andExpect(jsonPath("$.canRenew").value(i == 0));
        }
        Timestamp due = jdbcTemplate.queryForObject("SELECT due_at FROM loans WHERE id = ?", Timestamp.class, loanId);
        assertEquals(42, Duration.between(Instant.now(), due.toInstant()).plusHours(1).toDays());

        mockMvc.perform(patch("/api/emprestimos/" + loanId + "/renovar").header("Authorization", bearer(readerToken)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Limite de 2 renovações atingido"));
    }

    @Test
    void overdueLoanCannotBeRenewedAndBlocksNewReservations() throws Exception {
        long loanId = reserveAndPickup(readerToken, "atraso", bookId);
        moveDueDate(loanId, Duration.ofDays(-1));

        mockMvc.perform(get("/api/emprestimos/" + loanId).header("Authorization", bearer(readerToken)))
            .andExpect(jsonPath("$.overdue").value(true));
        mockMvc.perform(patch("/api/emprestimos/" + loanId + "/renovar").header("Authorization", bearer(readerToken)))
            .andExpect(status().isConflict());

        long otherBook = createBook(staffToken, "Iracema", OTHER_VALID_ISBN, genreId, authorId, 1);
        mockMvc.perform(post("/api/emprestimos")
                .header("Authorization", bearer(readerToken))
                .header("Idempotency-Key", "atraso-2")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookId\":" + otherBook + "}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Você possui empréstimo em atraso. Devolva o livro para reservar outros"));
    }

    @Test
    void lateReturnGeneratesFineThatBlocksUntilSimulatedPayment() throws Exception {
        long loanId = reserveAndPickup(readerToken, "multa", bookId);
        moveDueDate(loanId, Duration.ofDays(-3).minusMinutes(5));

        mockMvc.perform(post("/api/balcao/emprestimos/" + loanId + "/devolucao").header("Authorization", bearer(staffToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fineInCents").value(800))
            .andExpect(jsonPath("$.fineStatus").value("PENDING"));

        reserve(readerToken, "multa-bloqueio", bookId, status().isConflict());

        paymentGateway.setNextOutcomeForTest(PaymentOutcome.DECLINED);
        mockMvc.perform(post("/api/emprestimos/" + loanId + "/multa/pagamento").header("Authorization", bearer(readerToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fineStatus").value("PENDING"))
            .andExpect(jsonPath("$.lastPaymentOutcome").value("DECLINED"));

        mockMvc.perform(post("/api/emprestimos/" + loanId + "/multa/pagamento").header("Authorization", bearer(otherReaderToken)))
            .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/emprestimos/" + loanId + "/multa/pagamento").header("Authorization", bearer(readerToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fineStatus").value("PAID"));

        reserve(readerToken, "multa-liberada", bookId, status().isCreated());

        mockMvc.perform(get("/api/balcao/resumo").header("Authorization", bearer(staffToken)))
            .andExpect(jsonPath("$.paidFinesInCents").value(800))
            .andExpect(jsonPath("$.pendingFinesInCents").value(0));
    }

    @Test
    void cannotPayFineThatDoesNotExistNorReturnTwice() throws Exception {
        long loanId = reserveAndPickup(readerToken, "sem-multa", bookId);

        mockMvc.perform(post("/api/emprestimos/" + loanId + "/multa/pagamento").header("Authorization", bearer(readerToken)))
            .andExpect(status().isConflict());

        mockMvc.perform(post("/api/balcao/emprestimos/" + loanId + "/devolucao").header("Authorization", bearer(staffToken)))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/balcao/emprestimos/" + loanId + "/devolucao").header("Authorization", bearer(staffToken)))
            .andExpect(status().isConflict());
        assertEquals(2, availableCopies(bookId));
    }

    private String isbnFor(int index) {
        String base = "97800009900" + index;
        int sum = 0;
        for (int i = 0; i < 12; i++) {
            int digit = base.charAt(i) - '0';
            sum += i % 2 == 0 ? digit : digit * 3;
        }
        return base + (10 - sum % 10) % 10;
    }
}
