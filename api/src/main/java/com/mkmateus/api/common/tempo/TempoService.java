package com.mkmateus.api.common.tempo;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

@Service
public class TempoService {

    private final Clock clock;

    public TempoService(Clock clock) {
        this.clock = clock;
    }

    public LocalDate hoje() {
        return LocalDate.now(clock);
    }

    public Instant agora() {
        return Instant.now(clock);
    }
}
