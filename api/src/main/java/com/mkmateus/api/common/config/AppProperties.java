package com.mkmateus.api.common.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@ConfigurationProperties(prefix = "app")
@Validated
public record AppProperties(
        @NotBlank String timezone,
        @NotBlank String baseUrl,
        @NotEmpty List<String> corsOrigins,
        @NotBlank @Size(min = 32) String jwtSecret,
        @NotBlank String codePepper,
        String googleClientId) {
}
