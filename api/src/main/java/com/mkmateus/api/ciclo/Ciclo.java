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
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "ciclo")
public class Ciclo extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false, updatable = false)
    private Usuario usuario;

    @Column(name = "data_inicio", nullable = false, updatable = false)
    private LocalDate dataInicio;

    @Column(name = "data_fim", nullable = false, updatable = false)
    private LocalDate dataFim;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusCiclo status = StatusCiclo.ABERTO;

    @Column(name = "fechado_em")
    private Instant fechadoEm;

    @Column(name = "total_bruto", precision = 14, scale = 2)
    private BigDecimal totalBruto;

    @Column(name = "total_comissao", precision = 14, scale = 2)
    private BigDecimal totalComissao;

    @Column(name = "total_descontos_lancamentos", precision = 14, scale = 2)
    private BigDecimal totalDescontosLancamentos;

    @Column(name = "total_descontos_ciclo", precision = 14, scale = 2)
    private BigDecimal totalDescontosCiclo;

    @Column(name = "total_liquido", precision = 14, scale = 2)
    private BigDecimal totalLiquido;

    @Version
    @Column(name = "versao", nullable = false)
    private Long versao;

    protected Ciclo() {
    }

    public Ciclo(Usuario usuario, LocalDate dataInicio, LocalDate dataFim) {
        this.usuario = usuario;
        this.dataInicio = dataInicio;
        this.dataFim = dataFim;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public LocalDate getDataInicio() {
        return dataInicio;
    }

    public LocalDate getDataFim() {
        return dataFim;
    }

    public StatusCiclo getStatus() {
        return status;
    }

    public void setStatus(StatusCiclo status) {
        this.status = status;
    }

    public Instant getFechadoEm() {
        return fechadoEm;
    }

    public void setFechadoEm(Instant fechadoEm) {
        this.fechadoEm = fechadoEm;
    }

    public BigDecimal getTotalBruto() {
        return totalBruto;
    }

    public void setTotalBruto(BigDecimal totalBruto) {
        this.totalBruto = totalBruto;
    }

    public BigDecimal getTotalComissao() {
        return totalComissao;
    }

    public void setTotalComissao(BigDecimal totalComissao) {
        this.totalComissao = totalComissao;
    }

    public BigDecimal getTotalDescontosLancamentos() {
        return totalDescontosLancamentos;
    }

    public void setTotalDescontosLancamentos(BigDecimal totalDescontosLancamentos) {
        this.totalDescontosLancamentos = totalDescontosLancamentos;
    }

    public BigDecimal getTotalDescontosCiclo() {
        return totalDescontosCiclo;
    }

    public void setTotalDescontosCiclo(BigDecimal totalDescontosCiclo) {
        this.totalDescontosCiclo = totalDescontosCiclo;
    }

    public BigDecimal getTotalLiquido() {
        return totalLiquido;
    }

    public void setTotalLiquido(BigDecimal totalLiquido) {
        this.totalLiquido = totalLiquido;
    }

    public Long getVersao() {
        return versao;
    }
}
