package org.example.catalogo.config;

import org.example.catalogo.service.UsuarioService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class AdminInitializer implements ApplicationRunner {

    private final UsuarioService usuarioService;
    private final String email;
    private final String password;

    public AdminInitializer(
        UsuarioService usuarioService,
        @Value("${catalogo.admin.email:}") String email,
        @Value("${catalogo.admin.password:}") String password
    ) {
        this.usuarioService = usuarioService;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!email.isBlank() && !password.isBlank()) {
            usuarioService.garantirAdministrador(email, password);
        }
    }
}
