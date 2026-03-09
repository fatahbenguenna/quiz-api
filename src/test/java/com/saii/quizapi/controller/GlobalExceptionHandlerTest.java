package com.saii.quizapi.controller;

import com.saii.quizapi.service.QuizNotFoundException;
import com.saii.quizapi.service.SessionNotFoundException;
import com.saii.quizapi.service.SessionStateException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void should_return_404_when_quiz_not_found() {
        final var problem = handler.handleQuizNotFound(
                new QuizNotFoundException("Quiz introuvable : id=42"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(problem.getTitle()).isEqualTo("Quiz non trouvé");
        assertThat(problem.getDetail()).contains("42");
    }

    @Test
    void should_return_404_when_session_not_found() {
        final var problem = handler.handleSessionNotFound(
                new SessionNotFoundException("Session introuvable"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(problem.getTitle()).isEqualTo("Session non trouvée");
        assertThat(problem.getDetail()).contains("introuvable");
    }

    @Test
    void should_return_409_when_session_state_invalid() {
        final var problem = handler.handleSessionState(
                new SessionStateException("Impossible de démarrer : état = completed"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getTitle()).isEqualTo("État de session invalide");
        assertThat(problem.getDetail()).contains("completed");
    }

    @Test
    void should_return_400_when_illegal_argument() {
        final var problem = handler.handleBadRequest(
                new IllegalArgumentException("Question 999 absente du quiz"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getTitle()).isEqualTo("Requête invalide");
        assertThat(problem.getDetail()).contains("999");
    }

    @Test
    void should_return_400_when_validation_fails() {
        final var bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "candidateName", "ne doit pas être vide"));
        final var ex = new MethodArgumentNotValidException(null, bindingResult);

        final var problem = handler.handleValidation(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getTitle()).isEqualTo("Données invalides");
        assertThat(problem.getDetail()).contains("candidateName");
    }

    @Test
    void should_return_500_when_unexpected_error() {
        final var problem = handler.handleUnexpected(
                new RuntimeException("Erreur inattendue"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(problem.getTitle()).isEqualTo("Erreur interne");
        assertThat(problem.getDetail()).isEqualTo("Une erreur interne est survenue");
    }
}
