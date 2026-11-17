package org.example.catalogo.service;

import java.time.LocalDate;
import org.example.catalogo.service.dto.LoanContracts.LoanSummaryView;
import org.example.catalogo.service.dto.LoanContracts.LoanView;
import org.example.catalogo.service.dto.PageQuery;
import org.example.catalogo.service.dto.PageResult;

public interface DeskService {

    record QueueFilter(String status, String term, boolean pending, String deadline, LocalDate from, LocalDate to) {}
    record Reader(Long id, String nome, String email, boolean ativo) {}
    record Attendance(LoanView reservation, int availableCopies) {}

    PageResult<LoanView> list(QueueFilter filter, PageQuery query);

    PageResult<Reader> searchReaders(String term, PageQuery query);

    Attendance prepare(Long readerId, Long bookId);

    LoanView confirmAssistedPickup(Long loanId, Long staffId);

    LoanView findByPickupCode(String pickupCode);

    LoanView confirmPickup(String pickupCode, Long staffId);

    LoanView registerReturn(Long loanId, Long staffId);

    LoanSummaryView summary();
}
