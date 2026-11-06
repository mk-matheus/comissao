package com.mkmateus.api.ciclo;

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

import java.time.LocalDate;

@Entity
@Table(name = "configuracao_ciclo")
public class ConfiguracaoCiclo extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false, updatable = false)
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoCiclo tipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "contagem", nullable = false, length = 20)
    private ContagemCiclo contagem;

    @Column(name = "data_inicial")
    private LocalDate dataInicial;

    @Column(name = "vigente_a_partir_de", nullable = false)
    private LocalDate vigenteAPartirDe;

    protected ConfiguracaoCiclo() {
    }

    public ConfiguracaoCiclo(Usuario usuario,
                             TipoCiclo tipo,
                             ContagemCiclo contagem,
                             LocalDate dataInicial,
                             LocalDate vigenteAPartirDe) {
        this.usuario = usuario;
        this.tipo = tipo;
        this.contagem = contagem;
        this.dataInicial = dataInicial;
        this.vigenteAPartirDe = vigenteAPartirDe;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public TipoCiclo getTipo() {
        return tipo;
    }

    public void setTipo(TipoCiclo tipo) {
        this.tipo = tipo;
    }

    public ContagemCiclo getContagem() {
        return contagem;
    }

    public void setContagem(ContagemCiclo contagem) {
        this.contagem = contagem;
    }

    public LocalDate getDataInicial() {
        return dataInicial;
    }

    public void setDataInicial(LocalDate dataInicial) {
        this.dataInicial = dataInicial;
    }

    public LocalDate getVigenteAPartirDe() {
        return vigenteAPartirDe;
    }

    public void setVigenteAPartirDe(LocalDate vigenteAPartirDe) {
        this.vigenteAPartirDe = vigenteAPartirDe;
    }
}
