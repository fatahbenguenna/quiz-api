package com.saii.quizapi.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Expose un bean {@link Clock} pour permettre l'injection de temps
 * dans les services. Facilite les tests unitaires en remplaçant
 * le clock par un {@link Clock#fixed} dans les tests.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
