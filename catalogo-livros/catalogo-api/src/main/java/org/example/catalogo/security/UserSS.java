package org.example.catalogo.security;

import org.example.catalogo.model.enums.UsuarioPerfil;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public class UserSS implements UserDetails {

    private final Long id;
    private final String nome;
    private final String email;
    private final String password;
    private final boolean enabled;
    private final Set<UsuarioPerfil> perfis;

    public UserSS(Long id, String nome, String email, String password, boolean enabled, Collection<UsuarioPerfil> perfis) {
        this.id = id;
        this.nome = nome;
        this.email = email;
        this.password = password;
        this.enabled = enabled;
        this.perfis = Set.copyOf(perfis);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return perfis.stream()
            .map(perfil -> new SimpleGrantedAuthority("ROLE_" + perfil.name()))
            .toList();
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public List<String> getPerfis() {
        return perfis.stream().map(Enum::name).sorted().toList();
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
