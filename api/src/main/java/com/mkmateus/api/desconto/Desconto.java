package com.mkmateus.api.desconto;

import com.mkmateus.api.ciclo.Ciclo;
import com.mkmateus.api.common.jpa.BaseEntity;
import com.mkmateus.api.lancamento.Lancamento;
import com.mkmateus.api.usuario.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "desconto")
public class Desconto extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false, updatable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tipo_desconto_id", nullable = false)
    private TipoDesconto tipoDesconto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lancamento_id", updatable = false)
    private Lancamento lancamento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ciclo_id", updatable = false)
    private Ciclo ciclo;

    @Enumerated(EnumType.STRING)
    @Column(name = "modo", nullable = false, length = 20)
    private ModoDesconto modo;

    @Column(name = "valor_informado", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorInformado;

    @Column(name = "valor_calculado", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorCalculado;

    @Column(name = "descricao", length = 200)
    private String descricao;

    protected Desconto() {
    }

    private Desconto(Usuario usuario,
                     TipoDesconto tipoDesconto,
                     Lancamento lancamento,
                     Ciclo ciclo,
                     ModoDesconto modo,
                     BigDecimal valorInformado,
                     BigDecimal valorCalculado,
                     String descricao) {
        this.usuario = usuario;
        this.tipoDesconto = tipoDesconto;
        this.lancamento = lancamento;
        this.ciclo = ciclo;
        this.modo = modo;
        this.valorInformado = valorInformado;
        this.valorCalculado = valorCalculado;
        this.descricao = descricao;
    }

    public static Desconto paraLancamento(Usuario usuario,
                                          TipoDesconto tipoDesconto,
                                          Lancamento lancamento,
                                          ModoDesconto modo,
                                          BigDecimal valorInformado,
                                          BigDecimal valorCalculado,
                                          String descricao) {
        return new Desconto(usuario, tipoDesconto, lancamento, null, modo, valorInformado, valorCalculado, descricao);
    }

    public static Desconto paraCiclo(Usuario usuario,
                                     TipoDesconto tipoDesconto,
                                     Ciclo ciclo,
                                     ModoDesconto modo,
                                     BigDecimal valorInformado,
                                     BigDecimal valorCalculado,
                                     String descricao) {
        return new Desconto(usuario, tipoDesconto, null, ciclo, modo, valorInformado, valorCalculado, descricao);
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public TipoDesconto getTipoDesconto() {
        return tipoDesconto;
    }

    public void setTipoDesconto(TipoDesconto tipoDesconto) {
        this.tipoDesconto = tipoDesconto;
    }

    public Lancamento getLancamento() {
        return lancamento;
    }

    public Ciclo getCiclo() {
        return ciclo;
    }

    public ModoDesconto getModo() {
        return modo;
    }

    public void setModo(ModoDesconto modo) {
        this.modo = modo;
    }

    public BigDecimal getValorInformado() {
        return valorInformado;
    }

    public void setValorInformado(BigDecimal valorInformado) {
        this.valorInformado = valorInformado;
    }

    public BigDecimal getValorCalculado() {
        return valorCalculado;
    }

    public void setValorCalculado(BigDecimal valorCalculado) {
        this.valorCalculado = valorCalculado;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }
}
