package com.saii.quizapi.service;

/**
 * Levée quand une session de quiz est introuvable par son token.
 */
public class SessionNotFoundException extends RuntimeException {

    public SessionNotFoundException(final String message) {
        super(message);
    }
}
