package com.saii.quizapi.entity;

import java.util.Arrays;

/**
 * Type de reponse attendue pour une question.
 * Determine le composant d'edition cote frontend (Monaco Editor ou textarea).
 */
public enum AnswerType {

    CODE("code"),
    TEXT("text");

    private final String value;

    AnswerType(final String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static AnswerType fromValue(final String value) {
        return Arrays.stream(values())
                .filter(a -> a.value.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Type de reponse inconnu : " + value));
    }
}
