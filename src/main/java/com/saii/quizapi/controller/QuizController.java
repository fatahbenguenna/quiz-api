package com.saii.quizapi.controller;

import com.saii.quizapi.dto.CsvQuizRequestDTO;
import com.saii.quizapi.dto.MatchAndStartRequestDTO;
import com.saii.quizapi.dto.MatchRequestDTO;
import com.saii.quizapi.dto.QuizResponseDTO;
import com.saii.quizapi.dto.SessionResponseDTO;
import com.saii.quizapi.service.CsvQuizService;
import com.saii.quizapi.service.QuizMatcherService;
import com.saii.quizapi.service.QuizNotFoundException;
import com.saii.quizapi.service.QuizPdfService;
import com.saii.quizapi.service.QuizSessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/quiz")
@RequiredArgsConstructor
public class QuizController {

    private final QuizMatcherService matcherService;
    private final QuizPdfService pdfService;
    private final QuizSessionService sessionService;
    private final CsvQuizService csvQuizService;

    /**
     * GET /api/quiz/{id} — Récupère un quiz existant en JSON.
     */
    @GetMapping("/{id}")
    public ResponseEntity<QuizResponseDTO> getQuiz(@PathVariable final int id) {
        return matcherService.findQuizById(id)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new QuizNotFoundException("Quiz introuvable : id=" + id));
    }

    /**
     * GET /api/quiz/{id}/pdf — Génère et retourne le PDF d'un quiz existant.
     * Le frontend Angular ouvre ce PDF dans une popin.
     */
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> getQuizPdf(@PathVariable final int id) {
        final var quiz = matcherService.findQuizById(id)
                .orElseThrow(() -> new QuizNotFoundException("Quiz introuvable : id=" + id));

        return buildPdfResponse(quiz);
    }

    /**
     * POST /api/quiz/match — Le progiciel RH envoie les prérequis,
     * l'API assemble un quiz et retourne sa représentation JSON.
     */
    @PostMapping("/match")
    public ResponseEntity<QuizResponseDTO> matchQuiz(
            @Valid @RequestBody final MatchRequestDTO request) {
        final var quiz = matcherService.matchOrAssemble(request);
        return ResponseEntity.ok(quiz);
    }

    /**
     * POST /api/quiz/match/pdf — Même logique que /match mais retourne directement le PDF.
     */
    @PostMapping("/match/pdf")
    public ResponseEntity<byte[]> matchQuizPdf(
            @Valid @RequestBody final MatchRequestDTO request) {
        final var quiz = matcherService.matchOrAssemble(request);
        return buildPdfResponse(quiz);
    }

    /**
     * POST /api/quiz/match/session — Le progiciel RH envoie prérequis + candidat en un seul appel.
     * Assemble le quiz, crée la session et retourne l'URL d'accès pour le candidat.
     */
    @PostMapping("/match/session")
    public ResponseEntity<SessionResponseDTO> matchAndCreateSession(
            @Valid @RequestBody final MatchAndStartRequestDTO request) {
        final var session = sessionService.matchAndCreateSession(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(session);
    }

    /**
     * POST /api/quiz/csv/pdf — Génère un PDF de quiz à partir des technologies CSV.
     * Reçoit une liste de technologies (texte brut) et un niveau de séniorité,
     * sélectionne 3 questions par technologie et retourne le PDF.
     */
    @PostMapping("/csv/pdf")
    public ResponseEntity<byte[]> generatePdfFromCsv(
            @Valid @RequestBody final CsvQuizRequestDTO request) {
        final var quiz = csvQuizService.assembleFromCsv(request);
        return buildPdfResponse(quiz);
    }

    /**
     * POST /api/quiz/csv — Même logique que /csv/pdf mais retourne le JSON.
     */
    @PostMapping("/csv")
    public ResponseEntity<QuizResponseDTO> generateFromCsv(
            @Valid @RequestBody final CsvQuizRequestDTO request) {
        final var quiz = csvQuizService.assembleFromCsv(request);
        return ResponseEntity.ok(quiz);
    }

    private ResponseEntity<byte[]> buildPdfResponse(final QuizResponseDTO quiz) {
        final var pdfBytes = pdfService.generate(quiz);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=quiz-" + quiz.id() + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdfBytes.length)
                .body(pdfBytes);
    }
}
