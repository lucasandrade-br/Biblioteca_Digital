package org.example.catalogo.dto;

public record LoanSummaryResponse(
    long reservedCount,
    long activeCount,
    long overdueCount,
    long pendingFinesInCents,
    long paidFinesInCents
) {
}
