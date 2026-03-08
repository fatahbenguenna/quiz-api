package com.saii.quizapi.controller;

import com.saii.quizapi.dto.CreateSessionRequest;
import com.saii.quizapi.dto.SessionDetailResponse;
import com.saii.quizapi.dto.SessionResponse;
import com.saii.quizapi.dto.SubmitAnswerRequest;
import com.saii.quizapi.service.QuizSessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final QuizSessionService sessionService;

    /**
     * POST /api/sessions — Crée une session de quiz pour un candidat.
     * Retourne un token et une URL d'accès invocable par le progiciel RH.
     */
    @PostMapping
    public ResponseEntity<SessionResponse> createSession(
            @Valid @RequestBody final CreateSessionRequest request) {
        final var session = sessionService.createSession(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(session);
    }

    /**
     * GET /api/sessions/{token} — Récupère le détail complet d'une session.
     * Les réponses attendues ne sont visibles que quand la session est terminée.
     */
    @GetMapping("/{token}")
    public ResponseEntity<SessionDetailResponse> getSession(
            @PathVariable final String token) {
        final var detail = sessionService.getSessionByToken(token);
        return ResponseEntity.ok(detail);
    }

    /**
     * POST /api/sessions/{token}/start — Démarre la session (pending → in_progress).
     * Le candidat clique sur "Commencer le quiz".
     */
    @PostMapping("/{token}/start")
    public ResponseEntity<SessionDetailResponse> startSession(
            @PathVariable final String token) {
        final var detail = sessionService.startSession(token);
        return ResponseEntity.ok(detail);
    }

    /**
     * POST /api/sessions/{token}/answers — Soumet la réponse d'une question.
     * Idempotent : met à jour la réponse si elle existe déjà.
     */
    @PostMapping("/{token}/answers")
    public ResponseEntity<Void> submitAnswer(
            @PathVariable final String token,
            @Valid @RequestBody final SubmitAnswerRequest request) {
        sessionService.submitAnswer(token, request);
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /api/sessions/{token}/complete — Termine la session (in_progress → completed).
     * Le candidat clique sur "Terminer" ou le timer a expiré.
     */
    @PostMapping("/{token}/complete")
    public ResponseEntity<SessionDetailResponse> completeSession(
            @PathVariable final String token) {
        final var detail = sessionService.completeSession(token);
        return ResponseEntity.ok(detail);
    }
}
