CREATE TABLE lancamento (
    id UUID NOT NULL,
    usuario_id UUID NOT NULL,
    ciclo_id UUID NOT NULL,
    servico_id UUID NOT NULL,
    servico_nome VARCHAR(100) NOT NULL,
    cliente_id UUID NOT NULL,
    data DATE NOT NULL,
    valor_bruto NUMERIC(12, 2) NOT NULL,
    percentual NUMERIC(5, 2) NOT NULL,
    valor_comissao NUMERIC(12, 2) NOT NULL,
    observacao VARCHAR(500),
    excluido_em TIMESTAMPTZ,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    versao BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_lancamento PRIMARY KEY (id),
    CONSTRAINT uk_lancamento_usuario_id UNIQUE (usuario_id, id),
    CONSTRAINT fk_lancamento_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id),
    CONSTRAINT fk_lancamento_ciclo
        FOREIGN KEY (usuario_id, ciclo_id) REFERENCES ciclo (usuario_id, id),
    CONSTRAINT fk_lancamento_servico
        FOREIGN KEY (usuario_id, servico_id) REFERENCES servico (usuario_id, id),
    CONSTRAINT fk_lancamento_cliente
        FOREIGN KEY (usuario_id, cliente_id) REFERENCES cliente (usuario_id, id),
    CONSTRAINT ck_lancamento_valor_bruto CHECK (valor_bruto > 0),
    CONSTRAINT ck_lancamento_percentual CHECK (percentual >= 0 AND percentual <= 100),
    CONSTRAINT ck_lancamento_valor_comissao CHECK (valor_comissao >= 0)
);

CREATE TABLE desconto (
    id UUID NOT NULL,
    usuario_id UUID NOT NULL,
    tipo_desconto_id UUID NOT NULL,
    lancamento_id UUID,
    ciclo_id UUID,
    modo VARCHAR(20) NOT NULL,
    valor_informado NUMERIC(12, 2) NOT NULL,
    valor_calculado NUMERIC(12, 2) NOT NULL,
    descricao VARCHAR(200),
    criado_em TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_desconto PRIMARY KEY (id),
    CONSTRAINT fk_desconto_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id),
    CONSTRAINT fk_desconto_tipo_desconto
        FOREIGN KEY (usuario_id, tipo_desconto_id) REFERENCES tipo_desconto (usuario_id, id),
    CONSTRAINT fk_desconto_lancamento
        FOREIGN KEY (usuario_id, lancamento_id) REFERENCES lancamento (usuario_id, id),
    CONSTRAINT fk_desconto_ciclo
        FOREIGN KEY (usuario_id, ciclo_id) REFERENCES ciclo (usuario_id, id),
    CONSTRAINT ck_desconto_modo CHECK (modo IN ('VALOR', 'PERCENTUAL')),
    CONSTRAINT ck_desconto_valor_informado CHECK (valor_informado > 0),
    CONSTRAINT ck_desconto_valor_calculado CHECK (valor_calculado >= 0),
    CONSTRAINT ck_desconto_alvo_unico
        CHECK ((lancamento_id IS NOT NULL AND ciclo_id IS NULL)
            OR (lancamento_id IS NULL AND ciclo_id IS NOT NULL)),
    CONSTRAINT ck_desconto_percentual_maximo
        CHECK (modo <> 'PERCENTUAL' OR valor_informado <= 100)
);
