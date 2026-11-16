package org.example.catalogo.config;

public final class Constants {

    public static final String SECURITY_ROLE_ADMIN = "hasAnyRole('ADMIN')";
    public static final String SECURITY_ROLE_STAFF = "hasAnyRole('ADMIN','LIBRARIAN')";

    private Constants() {
    }
}
