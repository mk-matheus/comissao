package com.mkmateus.api.common.jpa;

import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositório base para recursos pertencentes a um usuário.
 * Não expõe {@code findById}, {@code deleteById} nem {@code findAll} sem filtro de {@code usuarioId},
 * exigindo isolamento explícito por usuário em todas as consultas.
 */
@NoRepositoryBean
public interface RecursoUsuarioRepository<T extends BaseEntity> extends Repository<T, UUID> {

    <S extends T> S save(S entity);

    <S extends T> List<S> saveAll(Iterable<S> entities);

    <S extends T> S saveAndFlush(S entity);

    void flush();

    void delete(T entity);

    Optional<T> findByIdAndUsuarioId(UUID id, UUID usuarioId);

    boolean existsByIdAndUsuarioId(UUID id, UUID usuarioId);
}
