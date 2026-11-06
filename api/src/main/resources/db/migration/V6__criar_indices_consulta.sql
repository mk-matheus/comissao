CREATE INDEX idx_lancamento_usuario_data_ativo
    ON lancamento (usuario_id, data)
    WHERE excluido_em IS NULL;

CREATE INDEX idx_lancamento_usuario_cliente
    ON lancamento (usuario_id, cliente_id);

CREATE INDEX idx_lancamento_usuario_ciclo
    ON lancamento (usuario_id, ciclo_id);

CREATE INDEX idx_lancamento_usuario_servico
    ON lancamento (usuario_id, servico_id);

CREATE INDEX idx_desconto_lancamento
    ON desconto (lancamento_id);

CREATE INDEX idx_desconto_ciclo
    ON desconto (ciclo_id);
