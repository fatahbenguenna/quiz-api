package com.saii.quizapi.service;

/**
 * Levée quand une transition d'état de session est invalide
 * (ex: tenter de soumettre une réponse sur une session complétée).
 */
public class SessionStateException extends RuntimeException {

    public SessionStateException(final String message) {
        super(message);
    }
}
