package com.mkmateus.api.auth;

import com.mkmateus.api.common.jpa.BaseEntity;
import com.mkmateus.api.usuario.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_token")
public class RefreshToken extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false, updatable = false)
    private Usuario usuario;

    @Column(name = "familia_id", nullable = false, updatable = false)
    private UUID familiaId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "token_hash", nullable = false, unique = true, updatable = false, length = 64)
    private String tokenHash;

    @Column(name = "expira_em", nullable = false, updatable = false)
    private Instant expiraEm;

    @Column(name = "revogado_em")
    private Instant revogadoEm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "substituido_por")
    private RefreshToken substituidoPor;

    protected RefreshToken() {
    }

    public RefreshToken(Usuario usuario, UUID familiaId, String tokenHash, Instant expiraEm) {
        this.usuario = usuario;
        this.familiaId = familiaId;
        this.tokenHash = tokenHash;
        this.expiraEm = expiraEm;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public UUID getFamiliaId() {
        return familiaId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getExpiraEm() {
        return expiraEm;
    }

    public Instant getRevogadoEm() {
        return revogadoEm;
    }

    public void setRevogadoEm(Instant revogadoEm) {
        this.revogadoEm = revogadoEm;
    }

    public RefreshToken getSubstituidoPor() {
        return substituidoPor;
    }

    public void setSubstituidoPor(RefreshToken substituidoPor) {
        this.substituidoPor = substituidoPor;
    }
}
