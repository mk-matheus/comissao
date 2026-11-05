package com.mkmateus.api.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class TempoConfig {

    @Bean
    Clock clock(AppProperties props) {
        return Clock.system(ZoneId.of(props.timezone()));
    }
}
