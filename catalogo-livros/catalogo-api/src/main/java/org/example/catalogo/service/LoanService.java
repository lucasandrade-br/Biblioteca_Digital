package org.example.catalogo.service;

import org.example.catalogo.service.dto.LoanContracts.LoanView;
import org.example.catalogo.service.dto.PageQuery;
import org.example.catalogo.service.dto.PageResult;

public interface LoanService {

    LoanView reserve(Long bookId, String idempotencyKey, Long readerId);

    LoanView cancel(Long loanId, Long readerId);

    LoanView renew(Long loanId, Long readerId);

    LoanView payFine(Long loanId, Long readerId);

    LoanView lendAtDesk(Long readerId, Long bookId, String idempotencyKey, Long staffId);

    PageResult<LoanView> listMine(Long readerId, String status, String term, String scope, PageQuery query);

    LoanView getMine(Long loanId, Long readerId);
}
