package com.mkmateus.api.common.erro;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ProblemDetail handleApi(ApiException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(ex.code().status(), ex.getMessage());
        pd.setTitle(ex.code().titulo());
        pd.setProperty("code", ex.code().name());
        ex.extras().forEach(pd::setProperty);
        return pd;
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> Map.of("campo", f.getField(),
                        "mensagem", Objects.toString(f.getDefaultMessage(), "inválido")))
                .toList();
        return handleExceptionInternal(ex, validacao(fields), headers, HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail handleConstraint(ConstraintViolationException ex) {
        List<Map<String, String>> fields = ex.getConstraintViolations().stream()
                .map(v -> Map.of("campo", v.getPropertyPath().toString(), "mensagem", v.getMessage()))
                .toList();
        return validacao(fields);
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleGeneric(Exception ex) {
        log.error("Erro não tratado", ex);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                ErrorCode.ERRO_INTERNO.status(), ErrorCode.ERRO_INTERNO.titulo());
        pd.setTitle(ErrorCode.ERRO_INTERNO.titulo());
        pd.setProperty("code", ErrorCode.ERRO_INTERNO.name());
        return pd;
    }

    private ProblemDetail validacao(List<Map<String, String>> fields) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Um ou mais campos são inválidos.");
        pd.setTitle(ErrorCode.VALIDACAO.titulo());
        pd.setProperty("code", ErrorCode.VALIDACAO.name());
        pd.setProperty("fields", fields);
        return pd;
    }
}