package com.mkmateus.api.cliente;

import com.mkmateus.api.common.jpa.BaseEntity;
import com.mkmateus.api.usuario.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "cliente")
public class Cliente extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false, updatable = false)
    private Usuario usuario;

    @Column(name = "nome", nullable = false, length = 120)
    private String nome;

    @Column(name = "nome_normalizado", nullable = false, length = 120)
    private String nomeNormalizado;

    @Column(name = "telefone", length = 20)
    private String telefone;

    @Column(name = "observacao", length = 500)
    private String observacao;

    @Column(name = "ativo", nullable = false)
    private boolean ativo = true;

    protected Cliente() {
    }

    public Cliente(Usuario usuario,
                   String nome,
                   String nomeNormalizado,
                   String telefone,
                   String observacao) {
        this.usuario = usuario;
        this.nome = nome;
        this.nomeNormalizado = nomeNormalizado;
        this.telefone = telefone;
        this.observacao = observacao;
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

    public String getNomeNormalizado() {
        return nomeNormalizado;
    }

    public void setNomeNormalizado(String nomeNormalizado) {
        this.nomeNormalizado = nomeNormalizado;
    }

    public String getTelefone() {
        return telefone;
    }

    public void setTelefone(String telefone) {
        this.telefone = telefone;
    }

    public String getObservacao() {
        return observacao;
    }

    public void setObservacao(String observacao) {
        this.observacao = observacao;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
    }
}
