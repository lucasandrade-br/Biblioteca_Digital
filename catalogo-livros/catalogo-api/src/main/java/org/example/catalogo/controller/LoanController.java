package org.example.catalogo.controller;

import org.example.catalogo.dto.CreateLoanRequest;
import org.example.catalogo.dto.LoanResponse;
import org.example.catalogo.model.enums.LoanStatus;
import org.example.catalogo.security.UserSS;
import org.example.catalogo.service.LoanService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/emprestimos")
public class LoanController {

    private final LoanService loanService;

    public LoanController(LoanService loanService) {
        this.loanService = loanService;
    }

    @PostMapping
    public ResponseEntity<LoanResponse> reserve(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @RequestBody @Valid CreateLoanRequest request,
        @AuthenticationPrincipal UserSS reader
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(LoanHttpMapper.toResponse(
            loanService.reserve(request.bookId(), idempotencyKey, reader.getId())
        ));
    }

    @GetMapping
    public ResponseEntity<Page<LoanResponse>> listMine(
        @AuthenticationPrincipal UserSS reader,
        @RequestParam(required = false) String q,
        @RequestParam(defaultValue = "ALL") String scope,
        @RequestParam(required = false) LoanStatus status,
        @PageableDefault(size = 20, sort = "requestedAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        var result = loanService.listMine(
            reader.getId(), status == null ? null : status.name(), q, scope, PageHttpMapper.toQuery(pageable)
        );
        return ResponseEntity.ok(PageHttpMapper.toPage(result, LoanHttpMapper::toResponse));
    }

    @GetMapping("/{loanId}")
    public ResponseEntity<LoanResponse> getMine(@PathVariable Long loanId, @AuthenticationPrincipal UserSS reader) {
        return ResponseEntity.ok(LoanHttpMapper.toResponse(loanService.getMine(loanId, reader.getId())));
    }

    @PatchMapping("/{loanId}/cancelar")
    public ResponseEntity<LoanResponse> cancel(@PathVariable Long loanId, @AuthenticationPrincipal UserSS reader) {
        return ResponseEntity.ok(LoanHttpMapper.toResponse(loanService.cancel(loanId, reader.getId())));
    }

    @PatchMapping("/{loanId}/renovar")
    public ResponseEntity<LoanResponse> renew(@PathVariable Long loanId, @AuthenticationPrincipal UserSS reader) {
        return ResponseEntity.ok(LoanHttpMapper.toResponse(loanService.renew(loanId, reader.getId())));
    }

    @PostMapping("/{loanId}/multa/pagamento")
    public ResponseEntity<LoanResponse> payFine(@PathVariable Long loanId, @AuthenticationPrincipal UserSS reader) {
        return ResponseEntity.ok(LoanHttpMapper.toResponse(loanService.payFine(loanId, reader.getId())));
    }
}
