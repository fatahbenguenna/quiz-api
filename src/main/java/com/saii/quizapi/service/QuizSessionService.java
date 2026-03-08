package com.saii.quizapi.service;

import com.saii.quizapi.dto.CreateSessionRequest;
import com.saii.quizapi.dto.MatchAndStartRequest;
import com.saii.quizapi.dto.MatchRequest;
import com.saii.quizapi.dto.SessionDetailResponse;
import com.saii.quizapi.dto.SessionResponse;
import com.saii.quizapi.dto.SubmitAnswerRequest;
import com.saii.quizapi.entity.QuizSession;
import com.saii.quizapi.entity.QuizSessionAnswer;
import com.saii.quizapi.entity.SessionStatus;
import com.saii.quizapi.repository.QuizSessionAnswerRepository;
import com.saii.quizapi.repository.QuizSessionRepository;
import com.saii.quizapi.repository.QuizTemplateRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.stream.Collectors;

@Service
@Slf4j
public class QuizSessionService {

    private final QuizSessionRepository sessionRepository;
    private final QuizSessionAnswerRepository answerRepository;
    private final QuizTemplateRepository quizTemplateRepository;
    private final QuizMatcherService matcherService;
    private final String quizAppBaseUrl;

    public QuizSessionService(
            final QuizSessionRepository sessionRepository,
            final QuizSessionAnswerRepository answerRepository,
            final QuizTemplateRepository quizTemplateRepository,
            final QuizMatcherService matcherService,
            @Value("${saii.quiz-app.base-url:http://localhost:4200}") final String quizAppBaseUrl) {
        this.sessionRepository = sessionRepository;
        this.answerRepository = answerRepository;
        this.quizTemplateRepository = quizTemplateRepository;
        this.matcherService = matcherService;
        this.quizAppBaseUrl = quizAppBaseUrl;
    }

    /**
     * Crée une nouvelle session pour un quiz donné.
     * Génère un token UUID unique et retourne l'URL d'accès pour le candidat.
     */
    @Transactional
    public SessionResponse createSession(final CreateSessionRequest request) {
        final var quiz = quizTemplateRepository.findById(request.quizId())
                .orElseThrow(() -> new QuizNotFoundException(
                        "Quiz introuvable : id=" + request.quizId()));

        final var session = new QuizSession(quiz, request.candidateName(), request.candidateEmail());
        final var saved = sessionRepository.save(session);

        log.info("Session créée : id={}, quiz={}", saved.getId(), quiz.getId());

        return toSessionResponse(saved);
    }

    /**
     * Assemble un quiz à partir des prérequis métier et crée immédiatement une session candidat.
     * Point d'entrée unique pour le progiciel RH (un seul appel HTTP).
     */
    @Transactional
    public SessionResponse matchAndCreateSession(final MatchAndStartRequest request) {
        final var matchRequest = new MatchRequest(
                request.jobTitle(),
                request.prerequisites(),
                request.maxQuestions()
        );
        final var quiz = matcherService.matchOrAssemble(matchRequest);

        final var sessionRequest = new CreateSessionRequest(
                quiz.id(),
                request.candidateName(),
                request.candidateEmail()
        );
        return createSession(sessionRequest);
    }

    /**
     * Récupère le détail complet d'une session par son token.
     * Auto-complète la session si le temps imparti est dépassé.
     * Les réponses attendues ne sont visibles que quand la session est terminée (vue interviewer).
     */
    @Transactional
    public SessionDetailResponse getSessionByToken(final String token) {
        final var session = findByTokenEagerOrThrow(token);
        autoCompleteIfExpired(session);
        return toSessionDetail(session);
    }

    /**
     * Démarre une session : passe de PENDING à IN_PROGRESS.
     * Le candidat clique sur "Commencer" dans l'app standalone.
     */
    @Transactional
    public SessionDetailResponse startSession(final String token) {
        final var session = findByTokenEagerOrThrow(token);

        if (session.getStatus() != SessionStatus.PENDING) {
            throw new SessionStateException(
                    "Impossible de démarrer la session : état actuel = " + session.getStatus().getValue());
        }

        session.setStatus(SessionStatus.IN_PROGRESS);
        session.setStartedAt(OffsetDateTime.now());
        sessionRepository.save(session);

        log.info("Session démarrée : id={}", session.getId());
        return toSessionDetail(session);
    }

    /**
     * Soumet ou met à jour la réponse d'un candidat pour une question donnée.
     * Idempotent : si une réponse existe déjà pour cette question, elle est mise à jour.
     */
    @Transactional
    public void submitAnswer(final String token, final SubmitAnswerRequest request) {
        final var session = findByTokenEagerOrThrow(token);

        autoCompleteIfExpired(session);

        if (session.getStatus() == SessionStatus.COMPLETED) {
            throw new SessionStateException("Impossible de soumettre : la session est terminée");
        }

        // Auto-démarrage si la session est encore en pending
        if (session.getStatus() == SessionStatus.PENDING) {
            session.setStatus(SessionStatus.IN_PROGRESS);
            session.setStartedAt(OffsetDateTime.now());
        }

        final var existingAnswer = answerRepository
                .findBySessionIdAndQuestionId(session.getId(), request.questionId());

        if (existingAnswer.isPresent()) {
            final var answer = existingAnswer.get();
            answer.setCandidateAnswer(request.candidateAnswer());
            answerRepository.save(answer);
            log.debug("Réponse mise à jour : session={}, question={}", session.getId(), request.questionId());
        } else {
            final var question = session.getQuizTemplate().getQuizQuestions().stream()
                    .filter(link -> link.getQuestion().getId().equals(request.questionId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Question " + request.questionId() + " absente du quiz de cette session"))
                    .getQuestion();

            final var answer = new QuizSessionAnswer(session, question, request.candidateAnswer());
            answerRepository.save(answer);
            log.debug("Réponse enregistrée : session={}, question={}", session.getId(), request.questionId());
        }
    }

    /**
     * Termine une session : passe de IN_PROGRESS à COMPLETED.
     * Le candidat clique sur "Terminer" ou le timer expire.
     */
    @Transactional
    public SessionDetailResponse completeSession(final String token) {
        final var session = findByTokenEagerOrThrow(token);

        if (session.getStatus() != SessionStatus.IN_PROGRESS) {
            throw new SessionStateException(
                    "Impossible de terminer la session : état actuel = " + session.getStatus().getValue());
        }

        session.setStatus(SessionStatus.COMPLETED);
        session.setCompletedAt(OffsetDateTime.now());
        sessionRepository.save(session);

        log.info("Session terminée : id={}", session.getId());
        return toSessionDetail(session);
    }

    /**
     * Auto-complète une session en cours si le temps imparti (durationMinutes du quiz) est dépassé.
     * Permet une expiration serveur transparente, indépendante du timer côté client.
     */
    private void autoCompleteIfExpired(final QuizSession session) {
        if (session.getStatus() != SessionStatus.IN_PROGRESS || session.getStartedAt() == null) {
            return;
        }

        final var durationMinutes = session.getQuizTemplate().getDurationMinutes();
        final var deadline = session.getStartedAt().plusMinutes(durationMinutes);

        if (OffsetDateTime.now().isAfter(deadline)) {
            session.setStatus(SessionStatus.COMPLETED);
            session.setCompletedAt(deadline);
            sessionRepository.save(session);
            log.info("Session auto-complétée (expiration) : id={}", session.getId());
        }
    }

    private QuizSession findByTokenEagerOrThrow(final String token) {
        return sessionRepository.findByTokenWithQuizAndQuestions(token)
                .orElseThrow(() -> new SessionNotFoundException("Session introuvable"));
    }

    private SessionResponse toSessionResponse(final QuizSession session) {
        final var sessionUrl = quizAppBaseUrl + "/session/" + session.getToken();
        return new SessionResponse(
                session.getId(),
                session.getToken(),
                sessionUrl,
                session.getQuizTemplate().getId(),
                session.getQuizTemplate().getTitle(),
                session.getCandidateName(),
                session.getStatus().getValue(),
                session.getCreatedAt()
        );
    }

    private SessionDetailResponse toSessionDetail(final QuizSession session) {
        final var quiz = session.getQuizTemplate();
        final var answers = answerRepository.findBySessionId(session.getId());
        final var isCompleted = session.getStatus() == SessionStatus.COMPLETED;

        // Pré-indexer les réponses par questionId (O(1) au lieu de O(N*M))
        final var answersByQuestionId = answers.stream()
                .collect(Collectors.toMap(
                        a -> a.getQuestion().getId(),
                        QuizSessionAnswer::getCandidateAnswer,
                        (existing, replacement) -> replacement));

        final var quizInfo = new SessionDetailResponse.QuizInfo(
                quiz.getId(),
                quiz.getTitle(),
                quiz.getDescription(),
                quiz.getTargetSeniority(),
                quiz.getDurationMinutes()
        );

        final var questions = quiz.getQuizQuestions().stream()
                .map(link -> {
                    final var q = link.getQuestion();
                    final var candidateAnswer = answersByQuestionId.get(q.getId());

                    // Masquer les réponses attendues tant que la session n'est pas terminée
                    return new SessionDetailResponse.SessionQuestionResponse(
                            q.getId(),
                            link.getPosition(),
                            q.getTechnology().getName(),
                            q.getTargetVersion(),
                            q.getSeniorityLevel(),
                            q.getQuestion(),
                            isCompleted ? q.getAnswer() : null,
                            isCompleted ? q.getExplanation() : null,
                            q.getDifficultyScore(),
                            candidateAnswer
                    );
                })
                .toList();

        return new SessionDetailResponse(
                session.getId(),
                session.getToken(),
                session.getStatus().getValue(),
                session.getCandidateName(),
                session.getStartedAt(),
                session.getCompletedAt(),
                quizInfo,
                questions
        );
    }
}
