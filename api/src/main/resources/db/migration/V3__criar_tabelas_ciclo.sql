CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE configuracao_ciclo (
    id UUID NOT NULL,
    usuario_id UUID NOT NULL,
    tipo VARCHAR(20) NOT NULL,
    contagem VARCHAR(20) NOT NULL,
    data_inicial DATE,
    vigente_a_partir_de DATE NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_configuracao_ciclo PRIMARY KEY (id),
    CONSTRAINT uk_configuracao_ciclo_usuario_id UNIQUE (usuario_id, id),
    CONSTRAINT uk_configuracao_ciclo_vigencia UNIQUE (usuario_id, vigente_a_partir_de),
    CONSTRAINT fk_configuracao_ciclo_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id),
    CONSTRAINT ck_configuracao_ciclo_tipo CHECK (tipo IN ('SEMANAL', 'QUINZENAL', 'MENSAL')),
    CONSTRAINT ck_configuracao_ciclo_contagem CHECK (contagem IN ('CALENDARIO', 'A_PARTIR_DE_DATA')),
    CONSTRAINT ck_configuracao_ciclo_data_inicial
        CHECK (contagem <> 'A_PARTIR_DE_DATA' OR data_inicial IS NOT NULL)
);

CREATE TABLE ciclo (
    id UUID NOT NULL,
    usuario_id UUID NOT NULL,
    data_inicio DATE NOT NULL,
    data_fim DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ABERTO',
    fechado_em TIMESTAMPTZ,
    total_bruto NUMERIC(14, 2),
    total_comissao NUMERIC(14, 2),
    total_descontos_lancamentos NUMERIC(14, 2),
    total_descontos_ciclo NUMERIC(14, 2),
    total_liquido NUMERIC(14, 2),
    versao BIGINT NOT NULL DEFAULT 0,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_ciclo PRIMARY KEY (id),
    CONSTRAINT uk_ciclo_usuario_id UNIQUE (usuario_id, id),
    CONSTRAINT uk_ciclo_usuario_inicio UNIQUE (usuario_id, data_inicio),
    CONSTRAINT fk_ciclo_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id),
    CONSTRAINT ck_ciclo_status CHECK (status IN ('ABERTO', 'FECHADO')),
    CONSTRAINT ck_ciclo_datas CHECK (data_fim >= data_inicio),
    CONSTRAINT ex_ciclo_sem_sobreposicao
        EXCLUDE USING gist (usuario_id WITH =, daterange(data_inicio, data_fim, '[]') WITH &&)
);
