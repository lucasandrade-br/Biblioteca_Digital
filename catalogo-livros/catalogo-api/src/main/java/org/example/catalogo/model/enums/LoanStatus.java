package org.example.catalogo.model.enums;

import java.util.List;

public enum LoanStatus {
    RESERVED,
    ACTIVE,
    RETURNED,
    CANCELLED,
    EXPIRED;

    public static final List<LoanStatus> OPEN_STATUSES = List.of(RESERVED, ACTIVE);
}
