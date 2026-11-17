package org.example.catalogo.controller;

import org.example.catalogo.dto.LoanResponse;
import org.example.catalogo.dto.LoanSummaryResponse;
import org.example.catalogo.dto.PickupRequest;
import org.example.catalogo.security.UserSS;
import java.time.LocalDate;
import org.example.catalogo.service.LoanService;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.example.catalogo.service.DeskService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.example.catalogo.config.Constants.SECURITY_ROLE_STAFF;

@RestController
@RequestMapping("/api/balcao")
@PreAuthorize(SECURITY_ROLE_STAFF)
public class DeskController {

    private final DeskService deskService;

    private final LoanService loans;

    public DeskController(DeskService deskService, LoanService loans) {
        this.deskService = deskService;
        this.loans = loans;
    }

    public record DirectLoan(@NotNull @Positive Long readerId, @NotNull @Positive Long bookId) {}

    public record Preparation(LoanResponse reservation, int availableCopies) {}

    @GetMapping("/emprestimos")
    public ResponseEntity<Page<LoanResponse>> list(
        @RequestParam(required = false) String q,
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "false") boolean pending,
        @RequestParam(defaultValue = "TODOS") String deadline,
        @RequestParam(required = false) LocalDate from,
        @RequestParam(required = false) LocalDate to,
        @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        var filter = new DeskService.QueueFilter(status, q, pending, deadline, from, to);
        var result = deskService.list(filter, PageHttpMapper.toQuery(pageable));
        return ResponseEntity.ok(PageHttpMapper.toPage(result, LoanHttpMapper::toResponse));
    }

    @GetMapping("/leitores")
    public ResponseEntity<Page<DeskService.Reader>> searchReaders(
        @RequestParam String q,
        @PageableDefault(size = 12, sort = "nome") Pageable pageable
    ) {
        var result = deskService.searchReaders(q, PageHttpMapper.toQuery(pageable));
        return ResponseEntity.ok(PageHttpMapper.toPage(result, reader -> reader));
    }

    @GetMapping("/emprestimos/preparar")
    public ResponseEntity<Preparation> prepare(@RequestParam Long readerId, @RequestParam Long bookId) {
        var preparation = deskService.prepare(readerId, bookId);
        var reservation = preparation.reservation();
        return ResponseEntity.ok(new Preparation(reservation == null ? null : LoanHttpMapper.toResponse(reservation),
            preparation.availableCopies()));
    }

    @PostMapping("/emprestimos")
    public ResponseEntity<LoanResponse> lend(
        @RequestBody @Valid DirectLoan request,
        @RequestHeader("Idempotency-Key") String key,
        @AuthenticationPrincipal UserSS staff
    ) {
        return ResponseEntity.ok(LoanHttpMapper.toResponse(loans.lendAtDesk(request.readerId(), request.bookId(), key, staff.getId())));
    }

    @PostMapping("/emprestimos/{loanId}/retirada-assistida")
    public ResponseEntity<LoanResponse> assistedPickup(@PathVariable Long loanId, @AuthenticationPrincipal UserSS staff) {
        return ResponseEntity.ok(LoanHttpMapper.toResponse(deskService.confirmAssistedPickup(loanId, staff.getId())));
    }

    @GetMapping("/retiradas/{pickupCode}")
    public ResponseEntity<LoanResponse> findByPickupCode(@PathVariable String pickupCode) {
        return ResponseEntity.ok(LoanHttpMapper.toResponse(deskService.findByPickupCode(pickupCode)));
    }

    @PostMapping("/retiradas")
    public ResponseEntity<LoanResponse> confirmPickup(
        @RequestBody @Valid PickupRequest request,
        @AuthenticationPrincipal UserSS staff
    ) {
        return ResponseEntity.ok(LoanHttpMapper.toResponse(deskService.confirmPickup(request.pickupCode(), staff.getId())));
    }

    @PostMapping("/emprestimos/{loanId}/devolucao")
    public ResponseEntity<LoanResponse> registerReturn(@PathVariable Long loanId, @AuthenticationPrincipal UserSS staff) {
        return ResponseEntity.ok(LoanHttpMapper.toResponse(deskService.registerReturn(loanId, staff.getId())));
    }

    @GetMapping("/resumo")
    public ResponseEntity<LoanSummaryResponse> summary() {
        return ResponseEntity.ok(LoanHttpMapper.toResponse(deskService.summary()));
    }
}
