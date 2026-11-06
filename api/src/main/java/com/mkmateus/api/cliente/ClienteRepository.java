package com.mkmateus.api.cliente;

import com.mkmateus.api.common.jpa.RecursoUsuarioRepository;

import java.util.Optional;
import java.util.UUID;

public interface ClienteRepository extends RecursoUsuarioRepository<Cliente> {

    Optional<Cliente> findByUsuarioIdAndNomeNormalizado(UUID usuarioId, String nomeNormalizado);
}
