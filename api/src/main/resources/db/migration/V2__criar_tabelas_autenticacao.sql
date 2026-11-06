CREATE TABLE codigo_verificacao (
    id UUID NOT NULL,
    usuario_id UUID NOT NULL,
    tipo VARCHAR(30) NOT NULL,
    codigo_hash CHAR(64) NOT NULL,
    expira_em TIMESTAMPTZ NOT NULL,
    tentativas SMALLINT NOT NULL DEFAULT 0,
    usado_em TIMESTAMPTZ,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_codigo_verificacao PRIMARY KEY (id),
    CONSTRAINT fk_codigo_verificacao_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id),
    CONSTRAINT ck_codigo_verificacao_tipo CHECK (tipo IN ('VERIFICACAO_EMAIL', 'RECUPERACAO_SENHA')),
    CONSTRAINT ck_codigo_verificacao_tentativas CHECK (tentativas >= 0)
);

CREATE INDEX idx_codigo_verificacao_usuario_tipo_criado
    ON codigo_verificacao (usuario_id, tipo, criado_em DESC);

CREATE TABLE refresh_token (
    id UUID NOT NULL,
    usuario_id UUID NOT NULL,
    familia_id UUID NOT NULL,
    token_hash CHAR(64) NOT NULL,
    expira_em TIMESTAMPTZ NOT NULL,
    revogado_em TIMESTAMPTZ,
    substituido_por UUID,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_refresh_token PRIMARY KEY (id),
    CONSTRAINT uk_refresh_token_usuario_id UNIQUE (usuario_id, id),
    CONSTRAINT uk_refresh_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_token_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id),
    CONSTRAINT fk_refresh_token_substituido_por
        FOREIGN KEY (usuario_id, substituido_por) REFERENCES refresh_token (usuario_id, id)
);

CREATE INDEX idx_refresh_token_familia_id ON refresh_token (familia_id);
CREATE INDEX idx_refresh_token_usuario_id ON refresh_token (usuario_id);
