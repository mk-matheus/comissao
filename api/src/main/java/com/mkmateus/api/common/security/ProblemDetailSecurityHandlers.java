package com.mkmateus.api.common.security;

import tools.jackson.databind.ObjectMapper;
import com.mkmateus.api.common.erro.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class ProblemDetailSecurityHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper mapper;

    public ProblemDetailSecurityHandlers(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void commence(HttpServletRequest req, HttpServletResponse res,
                         AuthenticationException ex) throws IOException {
        escrever(res, ErrorCode.NAO_AUTENTICADO);
    }

    @Override
    public void handle(HttpServletRequest req, HttpServletResponse res,
                       AccessDeniedException ex) throws IOException {
        escrever(res, ErrorCode.ACESSO_NEGADO);
    }

    private void escrever(HttpServletResponse res, ErrorCode code) throws IOException {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(code.status(), code.titulo());
        pd.setTitle(code.titulo());
        pd.setProperty("code", code.name());
        res.setStatus(code.status().value());
        res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        mapper.writeValue(res.getOutputStream(), pd);
    }
}
