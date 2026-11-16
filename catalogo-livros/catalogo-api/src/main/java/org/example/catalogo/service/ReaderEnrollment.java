package org.example.catalogo.service;

import org.example.catalogo.service.dto.PageQuery;
import org.example.catalogo.service.dto.PageResult;
import java.time.Instant;

public interface ReaderEnrollment {
    record Reader(Long id, String nome, String email, boolean ativo, boolean activationPending) {}
    record Enrollment(Reader reader, String activationCode, Instant expiresAt) {}

    PageResult<Reader> search(String q, PageQuery query);
    Enrollment enroll(String name, String email);
    Enrollment reissue(Long readerId);
    void activate(String email, String code, String password);
}
