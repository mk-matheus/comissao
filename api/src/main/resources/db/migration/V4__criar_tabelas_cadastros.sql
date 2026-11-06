CREATE TABLE servico (
    id UUID NOT NULL,
    usuario_id UUID NOT NULL,
    nome VARCHAR(100) NOT NULL,
    valor_sugerido NUMERIC(12, 2),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_servico PRIMARY KEY (id),
    CONSTRAINT uk_servico_usuario_id UNIQUE (usuario_id, id),
    CONSTRAINT fk_servico_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id),
    CONSTRAINT ck_servico_valor_sugerido CHECK (valor_sugerido IS NULL OR valor_sugerido > 0)
);

CREATE TABLE cliente (
    id UUID NOT NULL,
    usuario_id UUID NOT NULL,
    nome VARCHAR(120) NOT NULL,
    nome_normalizado VARCHAR(120) NOT NULL,
    telefone VARCHAR(20),
    observacao VARCHAR(500),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_cliente PRIMARY KEY (id),
    CONSTRAINT uk_cliente_usuario_id UNIQUE (usuario_id, id),
    CONSTRAINT uk_cliente_usuario_nome_normalizado UNIQUE (usuario_id, nome_normalizado),
    CONSTRAINT fk_cliente_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id)
);

CREATE TABLE tipo_desconto (
    id UUID NOT NULL,
    usuario_id UUID NOT NULL,
    nome VARCHAR(60) NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_tipo_desconto PRIMARY KEY (id),
    CONSTRAINT uk_tipo_desconto_usuario_id UNIQUE (usuario_id, id),
    CONSTRAINT fk_tipo_desconto_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id)
);
