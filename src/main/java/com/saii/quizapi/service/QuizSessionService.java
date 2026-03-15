package com.saii.quizapi.service;

import com.saii.quizapi.dto.CreateSessionRequestDTO;
import com.saii.quizapi.dto.MatchAndStartRequestDTO;
import com.saii.quizapi.dto.SessionDetailResponseDTO;
import com.saii.quizapi.dto.SessionResponseDTO;
import com.saii.quizapi.dto.SubmitAnswerRequestDTO;
import com.saii.quizapi.entity.QuizSession;
import com.saii.quizapi.entity.QuizSessionAnswer;
import com.saii.quizapi.repository.QuizSessionAnswerRepository;
import com.saii.quizapi.repository.QuizSessionRepository;
import com.saii.quizapi.repository.QuizTemplateRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class QuizSessionService {

    private final QuizSessionRepository sessionRepository;
    private final QuizSessionAnswerRepository answerRepository;
    private final QuizTemplateRepository quizTemplateRepository;
    private final QuizMatcherService matcherService;
    private final Clock clock;
    private final String quizAppBaseUrl;

    public QuizSessionService(
            final QuizSessionRepository sessionRepository,
            final QuizSessionAnswerRepository answerRepository,
            final QuizTemplateRepository quizTemplateRepository,
            final QuizMatcherService matcherService,
            final Clock clock,
            @Value("${saii.quiz-app.base-url:http://localhost:4200}") final String quizAppBaseUrl) {
        this.sessionRepository = sessionRepository;
        this.answerRepository = answerRepository;
        this.quizTemplateRepository = quizTemplateRepository;
        this.matcherService = matcherService;
        this.clock = clock;
        this.quizAppBaseUrl = quizAppBaseUrl;
    }

    /**
     * Liste toutes les sessions, triées par date de création décroissante.
     */
    @Transactional(readOnly = true)
    public List<SessionResponseDTO> listSessions() {
        return sessionRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toSessionResponseDTO)
                .toList();
    }

    /**
     * Crée une nouvelle session pour un quiz donné.
     * Génère un token UUID unique et retourne l'URL d'accès pour le candidat.
     */
    @Transactional
    public SessionResponseDTO createSession(final CreateSessionRequestDTO request) {
        final var quiz = quizTemplateRepository.findById(request.quizId())
                .orElseThrow(() -> new QuizNotFoundException(
                        "Quiz introuvable : id=" + request.quizId()));

        final var session = new QuizSession(quiz, request.candidateName(), request.candidateEmail(), now());
        final var saved = sessionRepository.save(session);

        log.info("Session créée : id={}, quiz={}", saved.getId(), quiz.getId());

        return toSessionResponseDTO(saved);
    }

    /**
     * Assemble un quiz à partir des prérequis métier et crée immédiatement une session candidat.
     * Point d'entrée unique pour le progiciel RH (un seul appel HTTP).
     */
    @Transactional
    public SessionResponseDTO matchAndCreateSession(final MatchAndStartRequestDTO request) {
        final var matchRequest = request.toMatchRequest();
        final var quiz = matcherService.matchOrAssemble(matchRequest);

        final var sessionRequest = new CreateSessionRequestDTO(
                quiz.id(),
                request.candidateName(),
                request.candidateEmail()
        );
        return createSession(sessionRequest);
    }

    /**
     * Récupère le détail complet d'une session par son token.
     * Auto-complète la session si le temps imparti est dépassé.
     */
    @Transactional
    public SessionDetailResponseDTO getSessionByToken(final String token) {
        final var session = findByTokenEagerOrThrow(token);
        if (session.autoCompleteIfExpired(now())) {
            sessionRepository.save(session);
            log.info("Session auto-complétée (expiration) : id={}", session.getId());
        }
        return toSessionDetail(session);
    }

    /**
     * Démarre une session : passe de PENDING à IN_PROGRESS.
     */
    @Transactional
    public SessionDetailResponseDTO startSession(final String token) {
        final var session = findByTokenEagerOrThrow(token);
        session.start(now());
        sessionRepository.save(session);

        log.info("Session démarrée : id={}", session.getId());
        return toSessionDetail(session);
    }

    /**
     * Soumet ou met à jour la réponse d'un candidat pour une question donnée.
     * Idempotent : si une réponse existe déjà pour cette question, elle est mise à jour.
     */
    @Transactional
    public void submitAnswer(final String token, final SubmitAnswerRequestDTO request) {
        final var session = findByTokenEagerOrThrow(token);

        if (session.autoCompleteIfExpired(now())) {
            sessionRepository.save(session);
            log.info("Session auto-complétée (expiration) : id={}", session.getId());
        }

        session.ensureAcceptsAnswers(now());

        final var existingAnswer = answerRepository
                .findBySessionIdAndQuestionId(session.getId(), request.questionId());

        if (existingAnswer.isPresent()) {
            existingAnswer.get().updateAnswer(request.candidateAnswer());
            answerRepository.save(existingAnswer.get());
            log.debug("Réponse mise à jour : session={}, question={}", session.getId(), request.questionId());
        } else {
            saveNewAnswer(session, request);
        }
    }

    /**
     * Termine une session : passe de IN_PROGRESS à COMPLETED.
     */
    @Transactional
    public SessionDetailResponseDTO completeSession(final String token) {
        final var session = findByTokenEagerOrThrow(token);
        session.complete(now());
        sessionRepository.save(session);

        log.info("Session terminée : id={}", session.getId());
        return toSessionDetail(session);
    }

    private void saveNewAnswer(final QuizSession session, final SubmitAnswerRequestDTO request) {
        final var question = session.getQuizTemplate().getQuizQuestions().stream()
                .filter(link -> link.getQuestion().getId().equals(request.questionId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Question " + request.questionId() + " absente du quiz de cette session"))
                .getQuestion();

        final var answer = new QuizSessionAnswer(session, question, request.candidateAnswer(), now());
        answerRepository.save(answer);
        log.debug("Réponse enregistrée : session={}, question={}", session.getId(), request.questionId());
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    private QuizSession findByTokenEagerOrThrow(final String token) {
        return sessionRepository.findByTokenWithQuizAndQuestions(token)
                .orElseThrow(() -> new SessionNotFoundException("Session introuvable"));
    }

    private SessionResponseDTO toSessionResponseDTO(final QuizSession session) {
        final var sessionUrl = quizAppBaseUrl + "/session/" + session.getToken();
        return new SessionResponseDTO(
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

    private SessionDetailResponseDTO toSessionDetail(final QuizSession session) {
        final var quiz = session.getQuizTemplate();
        final var answers = answerRepository.findBySessionId(session.getId());

        // Pré-indexer les réponses par questionId (O(1) au lieu de O(N*M))
        final var answersByQuestionId = answers.stream()
                .collect(Collectors.toMap(
                        a -> a.getQuestion().getId(),
                        QuizSessionAnswer::getCandidateAnswer,
                        (existing, replacement) -> replacement));

        final var quizInfo = new SessionDetailResponseDTO.QuizInfo(
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

                    return new SessionDetailResponseDTO.SessionQuestionResponse(
                            q.getId(),
                            link.getPosition(),
                            q.getTechnology().getName(),
                            q.getTargetVersion(),
                            q.getSeniorityLevel(),
                            q.getQuestion(),
                            session.isCompleted() ? q.getAnswer() : null,
                            session.isCompleted() ? q.getExplanation() : null,
                            q.getDifficultyScore(),
                            q.getAnswerType().getValue(),
                            candidateAnswer
                    );
                })
                .toList();

        return new SessionDetailResponseDTO(
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
