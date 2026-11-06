package com.mkmateus.api.lancamento;

import com.mkmateus.api.ciclo.Ciclo;
import com.mkmateus.api.cliente.Cliente;
import com.mkmateus.api.common.jpa.BaseEntity;
import com.mkmateus.api.servico.Servico;
import com.mkmateus.api.usuario.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "lancamento")
public class Lancamento extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false, updatable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ciclo_id", nullable = false)
    private Ciclo ciclo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "servico_id", nullable = false)
    private Servico servico;

    @Column(name = "servico_nome", nullable = false, length = 100)
    private String servicoNome;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @Column(name = "data", nullable = false)
    private LocalDate data;

    @Column(name = "valor_bruto", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorBruto;

    @Column(name = "percentual", nullable = false, precision = 5, scale = 2)
    private BigDecimal percentual;

    @Column(name = "valor_comissao", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorComissao;

    @Column(name = "observacao", length = 500)
    private String observacao;

    @Column(name = "excluido_em")
    private Instant excluidoEm;

    @Version
    @Column(name = "versao", nullable = false)
    private Long versao;

    protected Lancamento() {
    }

    public Lancamento(Usuario usuario,
                      Ciclo ciclo,
                      Servico servico,
                      String servicoNome,
                      Cliente cliente,
                      LocalDate data,
                      BigDecimal valorBruto,
                      BigDecimal percentual,
                      BigDecimal valorComissao,
                      String observacao) {
        this.usuario = usuario;
        this.ciclo = ciclo;
        this.servico = servico;
        this.servicoNome = servicoNome;
        this.cliente = cliente;
        this.data = data;
        this.valorBruto = valorBruto;
        this.percentual = percentual;
        this.valorComissao = valorComissao;
        this.observacao = observacao;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public Ciclo getCiclo() {
        return ciclo;
    }

    public void setCiclo(Ciclo ciclo) {
        this.ciclo = ciclo;
    }

    public Servico getServico() {
        return servico;
    }

    public void setServico(Servico servico) {
        this.servico = servico;
    }

    public String getServicoNome() {
        return servicoNome;
    }

    public void setServicoNome(String servicoNome) {
        this.servicoNome = servicoNome;
    }

    public Cliente getCliente() {
        return cliente;
    }

    public void setCliente(Cliente cliente) {
        this.cliente = cliente;
    }

    public LocalDate getData() {
        return data;
    }

    public void setData(LocalDate data) {
        this.data = data;
    }

    public BigDecimal getValorBruto() {
        return valorBruto;
    }

    public void setValorBruto(BigDecimal valorBruto) {
        this.valorBruto = valorBruto;
    }

    public BigDecimal getPercentual() {
        return percentual;
    }

    public void setPercentual(BigDecimal percentual) {
        this.percentual = percentual;
    }

    public BigDecimal getValorComissao() {
        return valorComissao;
    }

    public void setValorComissao(BigDecimal valorComissao) {
        this.valorComissao = valorComissao;
    }

    public String getObservacao() {
        return observacao;
    }

    public void setObservacao(String observacao) {
        this.observacao = observacao;
    }

    public Instant getExcluidoEm() {
        return excluidoEm;
    }

    public void setExcluidoEm(Instant excluidoEm) {
        this.excluidoEm = excluidoEm;
    }

    public Long getVersao() {
        return versao;
    }
}
