package com.mkmateus.api.servico;

import com.mkmateus.api.common.jpa.BaseEntity;
import com.mkmateus.api.usuario.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "servico")
public class Servico extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false, updatable = false)
    private Usuario usuario;

    @Column(name = "nome", nullable = false, length = 100)
    private String nome;

    @Column(name = "valor_sugerido", precision = 12, scale = 2)
    private BigDecimal valorSugerido;

    @Column(name = "ativo", nullable = false)
    private boolean ativo = true;

    protected Servico() {
    }

    public Servico(Usuario usuario, String nome, BigDecimal valorSugerido) {
        this.usuario = usuario;
        this.nome = nome;
        this.valorSugerido = valorSugerido;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public BigDecimal getValorSugerido() {
        return valorSugerido;
    }

    public void setValorSugerido(BigDecimal valorSugerido) {
        this.valorSugerido = valorSugerido;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
    }
}
