package org.example.catalogo.service.dto;

import java.util.Set;

public final class CoreServiceContracts {

    private CoreServiceContracts() {
    }

    public record CreateUserCommand(String name, String email, String password) {
    }

    public record UserView(Long id, String name, String email, boolean active, Set<String> roles) {
        public UserView {
            roles = Set.copyOf(roles);
        }
    }
}
