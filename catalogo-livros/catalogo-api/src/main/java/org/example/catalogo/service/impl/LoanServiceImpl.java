package org.example.catalogo.service.impl;

import org.example.catalogo.model.Book;
import org.example.catalogo.model.FinePayment;
import org.example.catalogo.model.Loan;
import org.example.catalogo.model.LoanPolicy;
import org.example.catalogo.model.Usuario;
import org.example.catalogo.model.enums.BookStatus;
import org.example.catalogo.model.enums.FineStatus;
import org.example.catalogo.model.enums.LoanStatus;
import org.example.catalogo.model.enums.PaymentOutcome;
import org.example.catalogo.model.enums.UsuarioPerfil;
import org.example.catalogo.repository.BookRepository;
import org.example.catalogo.repository.LoanRepository;
import org.example.catalogo.repository.UsuarioRepository;
import org.example.catalogo.service.LoanService;
import org.example.catalogo.service.PaymentGateway;
import org.example.catalogo.service.dto.LoanContracts.LoanView;
import org.example.catalogo.service.dto.LoanContracts.PaymentRequest;
import org.example.catalogo.service.dto.LoanContracts.PaymentResult;
import org.example.catalogo.service.dto.PageQuery;
import org.example.catalogo.service.dto.PageResult;
import org.example.catalogo.service.exception.ApplicationServiceException;
import org.example.catalogo.service.expiration.ReservationExpiration;
import org.example.catalogo.service.mapper.LoanViewMapper;
import org.example.catalogo.service.mapper.PageableMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;

@Service
public class LoanServiceImpl implements LoanService {

    private static final int MAX_OPEN_LOANS = 3;
    private static final String CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final LoanRepository loanRepository;
    private final BookRepository bookRepository;
    private final UsuarioRepository usuarioRepository;
    private final PaymentGateway paymentGateway;
    private final ReservationExpiration expiration;

    public LoanServiceImpl(
        LoanRepository loanRepository,
        BookRepository bookRepository,
        UsuarioRepository usuarioRepository,
        PaymentGateway paymentGateway,
        ReservationExpiration expiration
    ) {
        this.loanRepository = loanRepository;
        this.bookRepository = bookRepository;
        this.usuarioRepository = usuarioRepository;
        this.paymentGateway = paymentGateway;
        this.expiration = expiration;
    }

    @Override
    @Transactional
    public LoanView reserve(Long bookId, String idempotencyKey, Long readerId) {
        validateIdempotencyKey(idempotencyKey);
        expiration.expireForReservation(bookId, readerId, Instant.now());

        Usuario reader = usuarioRepository.findWithLockById(readerId)
            .orElseThrow(() -> new ApplicationServiceException(
                ApplicationServiceException.Code.UNAUTHORIZED, "Leitor autenticado não encontrado"
            ));
        if (!reader.isAtivo()) {
            throw new ApplicationServiceException(
                ApplicationServiceException.Code.UNAUTHORIZED, "A conta do leitor está inativa"
            );
        }
        if (!reader.getPerfis().contains(UsuarioPerfil.USER)) {
            throw new ApplicationServiceException(
                ApplicationServiceException.Code.FORBIDDEN, "Apenas leitores podem reservar livros"
            );
        }

        Instant now = Instant.now();
        var existing = loanRepository.findByReader_IdAndIdempotencyKey(readerId, idempotencyKey);
        if (existing.isPresent()) {
            if (!existing.get().getBook().getId().equals(bookId) || existing.get().getPickupCode() == null) {
                throw new IllegalArgumentException("Idempotency-Key já utilizada para outro livro");
            }
            return LoanViewMapper.forReader(existing.get(), now, hasPendingFine(readerId));
        }

        validateReaderCanBorrow(readerId, bookId, now);

        Book book = bookRepository.findWithLockById(bookId)
            .orElseThrow(() -> new ApplicationServiceException(
                ApplicationServiceException.Code.NOT_FOUND, "Livro não encontrado"
            ));
        if (book.getStatus() != BookStatus.ACTIVE) {
            throw new IllegalArgumentException("Livro indisponível no catálogo");
        }
        book.reserveCopy();

        Loan loan = new Loan(reader, book, idempotencyKey, generatePickupCode(), now, now.plus(LoanPolicy.PICKUP_WINDOW));
        return LoanViewMapper.forReader(loanRepository.saveAndFlush(loan), now, false);
    }

    @Override
    @Transactional
    public LoanView lendAtDesk(Long readerId, Long bookId, String key, Long staffId) {
        validateIdempotencyKey(key);
        expiration.expireForReservation(bookId, readerId, Instant.now());
        Usuario reader = usuarioRepository.findWithLockById(readerId).orElseThrow(() ->
            new ApplicationServiceException(ApplicationServiceException.Code.NOT_FOUND, "Leitor não encontrado"));
        if (!reader.isAtivo() || !reader.getPerfis().contains(UsuarioPerfil.USER)) {
            throw new IllegalArgumentException("Selecione uma conta de leitor ativa.");
        }
        Instant now = Instant.now();
        var existing = loanRepository.findByReader_IdAndIdempotencyKey(readerId, key);
        if (existing.isPresent()) {
            Loan loan = existing.get();
            if (loan.getPickupCode() != null || !loan.getBook().getId().equals(bookId)) {
                throw new IllegalArgumentException("Esta confirmação já foi usada em outro atendimento.");
            }
            return LoanViewMapper.forDesk(loan, now);
        }

        validateReaderCanBorrow(readerId, bookId, now);
        Book book = bookRepository.findWithLockById(bookId).orElseThrow(() ->
            new ApplicationServiceException(ApplicationServiceException.Code.NOT_FOUND, "Livro não encontrado"));
        if (book.getStatus() != BookStatus.ACTIVE) {
            throw new IllegalArgumentException("Livro indisponível no catálogo.");
        }
        Usuario staff = usuarioRepository.findById(staffId).orElseThrow(() ->
            new ApplicationServiceException(ApplicationServiceException.Code.UNAUTHORIZED, "Atendente não encontrado"));
        book.reserveCopy();
        Loan loan = Loan.lendAtDesk(reader, book, key, Instant.now(), staff.getNome());
        return LoanViewMapper.forDesk(loanRepository.saveAndFlush(loan), loan.getPickedUpAt());
    }

    @Override
    @Transactional
    public LoanView cancel(Long loanId, Long readerId) {
        Loan loan = findMineWithLock(loanId, readerId);
        Instant now = Instant.now();
        if (loan.getStatus() == LoanStatus.CANCELLED) {
            return LoanViewMapper.forReader(loan, now, hasPendingFine(readerId));
        }
        if (loan.getStatus() != LoanStatus.RESERVED) {
            throw new IllegalArgumentException("Somente reservas ainda não retiradas podem ser canceladas");
        }
        if (!LoanPolicy.isPickupOpen(loan, now)) {
            throw new IllegalArgumentException("Prazo de retirada expirado");
        }
        loan.cancel(now);
        releaseCopy(loan);
        return LoanViewMapper.forReader(loan, now, hasPendingFine(readerId));
    }

    @Override
    @Transactional
    public LoanView renew(Long loanId, Long readerId) {
        Loan loan = findMineWithLock(loanId, readerId);
        Instant now = Instant.now();
        boolean hasPendingFine = hasPendingFine(readerId);
        String blockReason = LoanPolicy.renewalBlockReason(loan, now, hasPendingFine);
        if (blockReason != null) {
            throw new IllegalArgumentException(blockReason);
        }
        loan.renew(loan.getDueAt().plus(LoanPolicy.RENEWAL_PERIOD), now);
        return LoanViewMapper.forReader(loan, now, hasPendingFine);
    }

    @Override
    @Transactional
    public LoanView payFine(Long loanId, Long readerId) {
        Loan loan = findMineWithLock(loanId, readerId);
        Instant now = Instant.now();
        if (loan.getFineStatus() == FineStatus.PAID) {
            return LoanViewMapper.forReader(loan, now, hasPendingFine(readerId));
        }
        if (loan.getFineStatus() != FineStatus.PENDING) {
            throw new IllegalArgumentException("Este empréstimo não possui multa pendente");
        }

        PaymentResult result = paymentGateway.start(new PaymentRequest(loan.getId(), loan.getFineInCents()));
        PaymentOutcome outcome = PaymentOutcome.valueOf(result.name());
        loan.addPayment(new FinePayment(
            loan,
            generateAttemptKey(),
            paymentGateway.providerName(),
            outcome,
            loan.getFineInCents(),
            now
        ));
        if (outcome == PaymentOutcome.APPROVED) {
            loan.markFinePaid(now);
        } else {
            loan.registerDeclinedPayment(now);
        }
        return LoanViewMapper.forReader(loan, now, hasPendingFine(readerId));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<LoanView> listMine(Long readerId, String status, String term, String scope, PageQuery query) {
        Instant now = Instant.now();
        boolean hasPendingFine = hasPendingFine(readerId);
        String normalized = term == null ? "" : term.trim().toLowerCase(Locale.ROOT);
        LoanStatus situation = status == null ? null : LoanStatus.valueOf(status);
        if (!java.util.Set.of("ALL", "RESERVAS", "EMPRESTIMOS", "HISTORICO").contains(scope)) {
            throw new IllegalArgumentException("Visão de empréstimos inválida.");
        }
        var page = loanRepository.searchMine(readerId, situation, normalized, scope, now, PageableMapper.toPageable(query));
        return PageableMapper.toResult(page.map(loan -> "HISTORICO".equals(scope)
            ? LoanViewMapper.forReaderHistory(loan, now, hasPendingFine)
            : LoanViewMapper.forReader(loan, now, hasPendingFine)), query);
    }

    @Override
    @Transactional(readOnly = true)
    public LoanView getMine(Long loanId, Long readerId) {
        Instant now = Instant.now();
        Loan loan = loanRepository.findByIdAndReader_Id(loanId, readerId).orElseThrow(this::loanNotFound);
        return LoanViewMapper.forReader(loan, now, hasPendingFine(readerId));
    }

    private boolean hasPendingFine(Long readerId) {
        return loanRepository.existsByReader_IdAndFineStatus(readerId, FineStatus.PENDING);
    }

    private void validateReaderCanBorrow(Long readerId, Long bookId, Instant now) {
        if (hasPendingFine(readerId)) {
            throw new IllegalArgumentException("Você possui multa pendente. Regularize para reservar novos livros");
        }
        if (loanRepository.existsByReader_IdAndStatusAndDueAtBefore(readerId, LoanStatus.ACTIVE, now)) {
            throw new IllegalArgumentException("Você possui empréstimo em atraso. Devolva o livro para reservar outros");
        }
        if (loanRepository.existsByReader_IdAndBook_IdAndStatusIn(readerId, bookId, LoanStatus.OPEN_STATUSES)) {
            throw new IllegalArgumentException("Você já possui uma reserva ou empréstimo deste livro");
        }
        if (loanRepository.countByReader_IdAndStatusIn(readerId, LoanStatus.OPEN_STATUSES) >= MAX_OPEN_LOANS) {
            throw new IllegalArgumentException("Limite de " + MAX_OPEN_LOANS + " livros simultâneos atingido");
        }
    }

    private void releaseCopy(Loan loan) {
        Book book = bookRepository.findWithLockById(loan.getBook().getId())
            .orElseThrow(() -> new IllegalStateException("Livro do empréstimo não encontrado"));
        book.releaseCopy();
    }

    private Loan findMineWithLock(Long loanId, Long readerId) {
        return loanRepository.findWithLockByIdAndReader_Id(loanId, readerId).orElseThrow(this::loanNotFound);
    }

    private ApplicationServiceException loanNotFound() {
        return new ApplicationServiceException(ApplicationServiceException.Code.NOT_FOUND, "Empréstimo não encontrado");
    }

    private void validateIdempotencyKey(String key) {
        if (key == null || key.isBlank() || key.length() > 128) {
            throw new IllegalArgumentException("Idempotency-Key obrigatória, com no máximo 128 caracteres");
        }
    }

    private String generatePickupCode() {
        StringBuilder code = new StringBuilder("LIV-");
        for (int i = 0; i < 8; i++) {
            code.append(CODE_ALPHABET.charAt(SECURE_RANDOM.nextInt(CODE_ALPHABET.length())));
        }
        return code.toString();
    }

    private String generateAttemptKey() {
        byte[] bytes = new byte[24];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
