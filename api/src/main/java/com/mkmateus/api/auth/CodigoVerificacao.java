package com.mkmateus.api.auth;

import com.mkmateus.api.common.jpa.BaseEntity;
import com.mkmateus.api.usuario.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "codigo_verificacao")
public class CodigoVerificacao extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false, updatable = false)
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, updatable = false, length = 30)
    private TipoCodigoVerificacao tipo;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "codigo_hash", nullable = false, updatable = false, length = 64)
    private String codigoHash;

    @Column(name = "expira_em", nullable = false, updatable = false)
    private Instant expiraEm;

    @Column(name = "tentativas", nullable = false)
    private short tentativas = 0;

    @Column(name = "usado_em")
    private Instant usadoEm;

    protected CodigoVerificacao() {
    }

    public CodigoVerificacao(Usuario usuario,
                             TipoCodigoVerificacao tipo,
                             String codigoHash,
                             Instant expiraEm) {
        this.usuario = usuario;
        this.tipo = tipo;
        this.codigoHash = codigoHash;
        this.expiraEm = expiraEm;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public TipoCodigoVerificacao getTipo() {
        return tipo;
    }

    public String getCodigoHash() {
        return codigoHash;
    }

    public Instant getExpiraEm() {
        return expiraEm;
    }

    public short getTentativas() {
        return tentativas;
    }

    public void setTentativas(short tentativas) {
        this.tentativas = tentativas;
    }

    public Instant getUsadoEm() {
        return usadoEm;
    }

    public void setUsadoEm(Instant usadoEm) {
        this.usadoEm = usadoEm;
    }
}
