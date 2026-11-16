package org.example.catalogo.model;

import org.example.catalogo.model.enums.UsuarioPerfil;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "usuarios")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String senha;

    @Column(nullable = false)
    private boolean ativo = true;

    @Column(name = "activation_hash")
    private String activationHash;

    @Column(name = "activation_expires_at")
    private Instant activationExpiresAt;

    @ElementCollection
    @CollectionTable(name = "usuario_perfis", joinColumns = @JoinColumn(name = "usuario_id"))
    @Column(name = "perfil", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private Set<UsuarioPerfil> perfis = new HashSet<>();

    protected Usuario() {
    }

    public Usuario(String nome, String email, String senha, UsuarioPerfil usuarioPerfil) {
        this.nome = nome;
        this.email = email;
        this.senha = senha;
        this.perfis.add(usuarioPerfil);
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getEmail() {
        return email;
    }

    public String getSenha() {
        return senha;
    }

    public boolean awaitsActivation() {
        return activationHash != null;
    }

    public boolean activationIsValidAt(Instant now) {
        return awaitsActivation() && activationExpiresAt.isAfter(now);
    }

    public String getActivationHash() {
        return activationHash;
    }

    public void issueActivation(String hash, Instant expiresAt) {
        this.activationHash = hash;
        this.activationExpiresAt = expiresAt;
    }

    public void activateWithPassword(String passwordHash) {
        this.senha = passwordHash;
        this.activationHash = null;
        this.activationExpiresAt = null;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
    }

    public Set<UsuarioPerfil> getPerfis() {
        return Collections.unmodifiableSet(perfis);
    }

    public void addPerfil(UsuarioPerfil usuarioPerfil) {
        perfis.add(usuarioPerfil);
    }

    public void removePerfil(UsuarioPerfil usuarioPerfil) {
        perfis.remove(usuarioPerfil);
    }
}
