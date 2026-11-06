package com.mkmateus.api.usuario;

import com.mkmateus.api.common.jpa.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "usuario")
public class Usuario extends BaseEntity {

    @Column(name = "nome", nullable = false, length = 120)
    private String nome;

    @Column(name = "username", nullable = false, unique = true, length = 30)
    private String username;

    @Column(name = "email", nullable = false, unique = true, length = 254)
    private String email;

    @Column(name = "email_verificado", nullable = false)
    private boolean emailVerificado = false;

    @Column(name = "senha_hash", length = 100)
    private String senhaHash;

    @Column(name = "google_id", unique = true, length = 64)
    private String googleId;

    protected Usuario() {
    }

    public Usuario(String nome, String username, String email, String senhaHash, String googleId) {
        this.nome = nome;
        this.username = username;
        this.email = email;
        this.senhaHash = senhaHash;
        this.googleId = googleId;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public boolean isEmailVerificado() {
        return emailVerificado;
    }

    public void setEmailVerificado(boolean emailVerificado) {
        this.emailVerificado = emailVerificado;
    }

    public String getSenhaHash() {
        return senhaHash;
    }

    public void setSenhaHash(String senhaHash) {
        this.senhaHash = senhaHash;
    }

    public String getGoogleId() {
        return googleId;
    }

    public void setGoogleId(String googleId) {
        this.googleId = googleId;
    }
}
