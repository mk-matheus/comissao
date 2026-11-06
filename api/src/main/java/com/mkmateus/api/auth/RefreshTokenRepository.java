package com.mkmateus.api.auth;

import com.mkmateus.api.common.jpa.RecursoUsuarioRepository;

import java.util.Optional;

public interface RefreshTokenRepository extends RecursoUsuarioRepository<RefreshToken> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);
}
