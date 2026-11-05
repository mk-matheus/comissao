package com.mkmateus.api.common.erro;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    VALIDACAO(HttpStatus.BAD_REQUEST, "Dados inválidos"),
    NAO_AUTENTICADO(HttpStatus.UNAUTHORIZED, "Sua sessão expirou. Entre novamente."),
    ACESSO_NEGADO(HttpStatus.FORBIDDEN, "Acesso negado"),
    RECURSO_NAO_ENCONTRADO(HttpStatus.NOT_FOUND, "Não encontramos este item."),
    ERRO_INTERNO(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno. Tente novamente.");

    private final HttpStatus status;
    private final String titulo;

    ErrorCode(HttpStatus status, String titulo) {
        this.status = status;
        this.titulo = titulo;
    }

    public HttpStatus status() { return status; }
    public String titulo() { return titulo; }
}