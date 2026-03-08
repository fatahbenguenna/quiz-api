package com.saii.quizapi.entity;

import java.util.Arrays;

/**
 * États possibles d'une session de quiz.
 * La valeur en base est stockée en snake_case (ex: "in_progress").
 */
public enum SessionStatus {

    PENDING("pending"),
    IN_PROGRESS("in_progress"),
    COMPLETED("completed");

    private final String value;

    SessionStatus(final String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static SessionStatus fromValue(final String value) {
        return Arrays.stream(values())
                .filter(s -> s.value.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Statut de session inconnu : " + value));
    }
}
