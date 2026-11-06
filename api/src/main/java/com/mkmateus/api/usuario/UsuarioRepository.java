package com.mkmateus.api.usuario;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

    Optional<Usuario> findByEmail(String email);

    Optional<Usuario> findByUsername(String username);

    Optional<Usuario> findByGoogleId(String googleId);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);
}
