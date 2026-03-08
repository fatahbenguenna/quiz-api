package com.saii.quizapi.controller;

import com.saii.quizapi.service.QuizNotFoundException;
import com.saii.quizapi.service.SessionNotFoundException;
import com.saii.quizapi.service.SessionStateException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(QuizNotFoundException.class)
    public ProblemDetail handleQuizNotFound(final QuizNotFoundException ex) {
        log.warn("Quiz non trouvé : {}", ex.getMessage());
        final var problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Quiz non trouvé");
        return problem;
    }

    @ExceptionHandler(SessionNotFoundException.class)
    public ProblemDetail handleSessionNotFound(final SessionNotFoundException ex) {
        log.warn("Session non trouvée : {}", ex.getMessage());
        final var problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Session non trouvée");
        return problem;
    }

    @ExceptionHandler(SessionStateException.class)
    public ProblemDetail handleSessionState(final SessionStateException ex) {
        log.warn("Transition de session invalide : {}", ex.getMessage());
        final var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("État de session invalide");
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleBadRequest(final IllegalArgumentException ex) {
        log.warn("Requête invalide : {}", ex.getMessage());
        final var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Requête invalide");
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(final MethodArgumentNotValidException ex) {
        final var errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + " : " + e.getDefaultMessage())
                .collect(Collectors.joining(", "));
        log.warn("Erreur de validation : {}", errors);
        final var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, errors);
        problem.setTitle("Données invalides");
        return problem;
    }

    /**
     * Handler catch-all : intercepte toutes les exceptions non prévues
     * pour éviter d'exposer des stack traces techniques au client.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(final Exception ex) {
        log.error("Erreur interne inattendue", ex);
        final var problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Une erreur interne est survenue");
        problem.setTitle("Erreur interne");
        return problem;
    }
}
