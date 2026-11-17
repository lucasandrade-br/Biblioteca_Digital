package org.example.catalogo.service.impl;

import org.example.catalogo.model.Book;
import org.example.catalogo.model.Loan;
import org.example.catalogo.model.LoanPolicy;
import org.example.catalogo.model.Usuario;
import org.example.catalogo.model.enums.BookStatus;
import org.example.catalogo.model.enums.UsuarioPerfil;
import org.example.catalogo.model.enums.FineStatus;
import org.example.catalogo.model.enums.LoanStatus;
import org.example.catalogo.repository.BookRepository;
import org.example.catalogo.repository.LoanRepository;
import org.example.catalogo.repository.UsuarioRepository;
import org.example.catalogo.service.DeskService;
import org.example.catalogo.service.dto.LoanContracts.LoanSummaryView;
import org.example.catalogo.service.dto.LoanContracts.LoanView;
import org.example.catalogo.service.dto.PageQuery;
import org.example.catalogo.service.dto.PageResult;
import org.example.catalogo.service.exception.ApplicationServiceException;
import org.example.catalogo.service.mapper.LoanViewMapper;
import org.example.catalogo.service.mapper.PageableMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;

@Service
public class DeskServiceImpl implements DeskService {

    private static final ZoneId LIBRARY_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final long FINE_PER_DAY_IN_CENTS = 200;

    private final LoanRepository loanRepository;
    private final BookRepository bookRepository;
    private final UsuarioRepository usuarioRepository;

    public DeskServiceImpl(
        LoanRepository loanRepository,
        BookRepository bookRepository,
        UsuarioRepository usuarioRepository
    ) {
        this.loanRepository = loanRepository;
        this.bookRepository = bookRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<LoanView> list(QueueFilter filter, PageQuery query) {
        Instant now = Instant.now();
        String status = filter.status();
        String term = filter.term();
        LoanStatus situation = status == null || status.isBlank() ? null : parseStatus(status);
        String normalized = term == null ? "" : term.trim().toLowerCase(Locale.ROOT);
        LocalDate today = LocalDate.ofInstant(now, LIBRARY_ZONE);
        LocalDate from = filter.from();
        LocalDate to = filter.to();
        boolean overdue = false;
        switch (filter.deadline()) {
            case "ATRASO" -> { overdue = true; from = null; to = null; }
            case "HOJE" -> { from = today; to = today; }
            case "PROXIMOS_7" -> { from = today; to = today.plusDays(6); }
            case "TODOS" -> { }
            default -> throw new IllegalArgumentException("Filtro de prazo inválido.");
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("A data inicial deve ser anterior ou igual à final.");
        }
        Instant start = from == null ? null : from.atStartOfDay(LIBRARY_ZONE).toInstant();
        Instant until = to == null ? null : to.plusDays(1).atStartOfDay(LIBRARY_ZONE).toInstant();
        var page = loanRepository.searchDesk(
            situation, normalized, filter.pending(), overdue, start, until, now, PageableMapper.toPageable(query)
        );
        return PageableMapper.toResult(page.map(loan -> LoanViewMapper.forDeskQueue(loan, now)), query);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Reader> searchReaders(String term, PageQuery query) {
        String normalized = term.trim().toLowerCase(Locale.ROOT);
        var pageable = PageableMapper.toPageable(query);
        var page = usuarioRepository.search(normalized, UsuarioPerfil.USER, null, pageable);
        return PageableMapper.toResult(page.map(user -> new Reader(user.getId(), user.getNome(),
            user.getEmail(), user.isAtivo())), query);
    }

    @Override
    @Transactional(readOnly = true)
    public Attendance prepare(Long readerId, Long bookId) {
        Usuario reader = usuarioRepository.findById(readerId).orElseThrow(() ->
            new ApplicationServiceException(ApplicationServiceException.Code.NOT_FOUND, "Leitor não encontrado"));
        if (!reader.isAtivo() || !reader.getPerfis().contains(UsuarioPerfil.USER)) {
            throw new IllegalArgumentException("Selecione uma conta de leitor ativa.");
        }
        Book book = bookRepository.findByIdAndStatus(bookId, BookStatus.ACTIVE).orElseThrow(() ->
            new ApplicationServiceException(ApplicationServiceException.Code.NOT_FOUND, "Livro ativo não encontrado"));
        Instant now = Instant.now();
        var reservation = loanRepository.findFirstByReader_IdAndBook_IdAndStatusAndPickupDeadlineAfterOrderById(
            readerId, bookId, LoanStatus.RESERVED, now
        ).map(loan -> LoanViewMapper.forDesk(loan, now)).orElse(null);
        return new Attendance(reservation, book.getAvailableCopies());
    }

    @Override
    @Transactional
    public LoanView confirmAssistedPickup(Long loanId, Long staffId) {
        Loan loan = loanRepository.findWithLockById(loanId).orElseThrow(() ->
            new ApplicationServiceException(ApplicationServiceException.Code.NOT_FOUND, "Reserva não encontrada"));
        return completePickup(loan, staffId);
    }

    @Override
    @Transactional(readOnly = true)
    public LoanView findByPickupCode(String pickupCode) {
        Loan loan = loanRepository.findByPickupCode(normalizeCode(pickupCode)).orElseThrow(this::codeNotFound);
        return LoanViewMapper.forDesk(loan, Instant.now());
    }

    @Override
    @Transactional
    public LoanView confirmPickup(String pickupCode, Long staffId) {
        Loan loan = loanRepository.findWithLockByPickupCode(normalizeCode(pickupCode)).orElseThrow(this::codeNotFound);
        if (loan.getStatus() != LoanStatus.RESERVED) {
            throw new IllegalArgumentException("Este código já foi utilizado ou a reserva não está mais ativa");
        }
        return completePickup(loan, staffId);
    }

    private LoanView completePickup(Loan loan, Long staffId) {
        Instant now = Instant.now();
        if (loan.getStatus() != LoanStatus.RESERVED) {
            throw new IllegalArgumentException("A retirada já foi registrada ou a reserva não está mais ativa");
        }
        if (!LoanPolicy.isPickupOpen(loan, now)) {
            throw new IllegalArgumentException("Prazo de retirada expirado");
        }
        loan.pickUp(now, now.plus(LoanPolicy.LOAN_PERIOD), findStaff(staffId).getNome());
        return LoanViewMapper.forDesk(loan, now);
    }

    @Override
    @Transactional
    public LoanView registerReturn(Long loanId, Long staffId) {
        Instant now = Instant.now();
        Loan loan = loanRepository.findWithLockById(loanId)
            .orElseThrow(() -> new ApplicationServiceException(
                ApplicationServiceException.Code.NOT_FOUND, "Empréstimo não encontrado"
            ));
        if (loan.getStatus() != LoanStatus.ACTIVE) {
            throw new IllegalArgumentException("Somente empréstimos ativos podem ser devolvidos");
        }
        loan.registerReturn(now, calculateFine(loan.getDueAt(), now), findStaff(staffId).getNome());
        Book book = bookRepository.findWithLockById(loan.getBook().getId())
            .orElseThrow(() -> new IllegalStateException("Livro do empréstimo não encontrado"));
        book.releaseCopy();
        return LoanViewMapper.forDesk(loan, now);
    }

    @Override
    @Transactional(readOnly = true)
    public LoanSummaryView summary() {
        return new LoanSummaryView(
            loanRepository.countByStatus(LoanStatus.RESERVED),
            loanRepository.countByStatus(LoanStatus.ACTIVE),
            loanRepository.countByStatusAndDueAtBefore(LoanStatus.ACTIVE, Instant.now()),
            loanRepository.sumFineByStatus(FineStatus.PENDING),
            loanRepository.sumFineByStatus(FineStatus.PAID)
        );
    }

    private long calculateFine(Instant dueAt, Instant returnedAt) {
        if (!returnedAt.isAfter(dueAt)) {
            return 0;
        }
        Duration late = Duration.between(dueAt, returnedAt);
        long lateDays = late.toDays();
        if (late.compareTo(Duration.ofDays(lateDays)) > 0) {
            lateDays++;
        }
        return lateDays * FINE_PER_DAY_IN_CENTS;
    }

    private LoanStatus parseStatus(String status) {
        try {
            return LoanStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Status de empréstimo inválido");
        }
    }

    private String normalizeCode(String pickupCode) {
        return pickupCode.trim().toUpperCase(Locale.ROOT);
    }

    private Usuario findStaff(Long staffId) {
        return usuarioRepository.findById(staffId)
            .orElseThrow(() -> new ApplicationServiceException(
                ApplicationServiceException.Code.UNAUTHORIZED, "Usuário autenticado não encontrado"
            ));
    }

    private ApplicationServiceException codeNotFound() {
        return new ApplicationServiceException(ApplicationServiceException.Code.NOT_FOUND, "Código de retirada não encontrado");
    }
}
