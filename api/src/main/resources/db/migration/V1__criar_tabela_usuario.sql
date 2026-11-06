CREATE TABLE usuario (
    id UUID NOT NULL,
    nome VARCHAR(120) NOT NULL,
    username VARCHAR(30) NOT NULL,
    email VARCHAR(254) NOT NULL,
    email_verificado BOOLEAN NOT NULL DEFAULT FALSE,
    senha_hash VARCHAR(100),
    google_id VARCHAR(64),
    criado_em TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_usuario PRIMARY KEY (id),
    CONSTRAINT uk_usuario_username UNIQUE (username),
    CONSTRAINT uk_usuario_email UNIQUE (email),
    CONSTRAINT uk_usuario_google_id UNIQUE (google_id),
    CONSTRAINT ck_usuario_username_formato CHECK (username ~ '^[a-z0-9._]{3,30}$'),
    CONSTRAINT ck_usuario_credencial CHECK (senha_hash IS NOT NULL OR google_id IS NOT NULL)
);

CREATE UNIQUE INDEX uk_usuario_email_lower ON usuario (LOWER(email));
