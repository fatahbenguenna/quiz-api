package com.saii.quizapi.service;

import com.saii.quizapi.dto.CreateSessionRequest;
import com.saii.quizapi.dto.SubmitAnswerRequest;
import com.saii.quizapi.entity.Question;
import com.saii.quizapi.entity.QuizSession;
import com.saii.quizapi.entity.QuizSessionAnswer;
import com.saii.quizapi.entity.QuizTemplate;
import com.saii.quizapi.entity.SessionStatus;
import com.saii.quizapi.repository.QuizSessionAnswerRepository;
import com.saii.quizapi.repository.QuizSessionRepository;
import com.saii.quizapi.repository.QuizTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static com.saii.quizapi.TestFixtures.createQuestionFull;
import static com.saii.quizapi.TestFixtures.createTechnology;
import static com.saii.quizapi.TestFixtures.setField;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuizSessionServiceTest {

    private static final String BASE_URL = "http://localhost:4200";

    @Mock
    private QuizSessionRepository sessionRepository;

    @Mock
    private QuizSessionAnswerRepository answerRepository;

    @Mock
    private QuizTemplateRepository quizTemplateRepository;

    @Mock
    private QuizMatcherService matcherService;

    private QuizSessionService service;

    private QuizTemplate quizTemplate;
    private Question question;

    @BeforeEach
    void setUp() {
        service = new QuizSessionService(
                sessionRepository, answerRepository, quizTemplateRepository, matcherService, BASE_URL);

        final var tech = createTechnology(1, "Java");
        question = createQuestionFull(10, tech, "confirme",
                "Qu'est-ce qu'un sealed class ?",
                "Une classe restreinte dans ses sous-types",
                "Introduit en Java 17", (short) 3, "21");

        quizTemplate = new QuizTemplate("Quiz Java", "Description", "confirme", 30);
        setField(quizTemplate, "id", 1);
        quizTemplate.addQuestion(question, (short) 1);
    }

    @Test
    void should_create_session_with_token_and_url() {
        when(quizTemplateRepository.findById(1)).thenReturn(Optional.of(quizTemplate));
        when(sessionRepository.save(any(QuizSession.class)))
                .thenAnswer(invocation -> {
                    final var session = invocation.getArgument(0, QuizSession.class);
                    setField(session, "id", 100);
                    return session;
                });

        final var request = new CreateSessionRequest(1, "Alice Dupont", "alice@example.com");
        final var response = service.createSession(request);

        assertThat(response.id()).isEqualTo(100);
        assertThat(response.token()).isNotBlank();
        assertThat(response.sessionUrl()).startsWith(BASE_URL + "/session/");
        assertThat(response.quizTitle()).isEqualTo("Quiz Java");
        assertThat(response.candidateName()).isEqualTo("Alice Dupont");
        assertThat(response.status()).isEqualTo("pending");
    }

    @Test
    void should_throw_when_quiz_not_found_for_session() {
        when(quizTemplateRepository.findById(999)).thenReturn(Optional.empty());

        final var request = new CreateSessionRequest(999, "Bob", "bob@example.com");

        assertThatThrownBy(() -> service.createSession(request))
                .isInstanceOf(QuizNotFoundException.class)
                .hasMessageContaining("999");
    }

    @Test
    void should_start_pending_session() {
        final var session = new QuizSession(quizTemplate, "Alice", "alice@example.com");
        setField(session, "id", 100);

        when(sessionRepository.findByTokenWithQuizAndQuestions(session.getToken()))
                .thenReturn(Optional.of(session));
        when(sessionRepository.save(any(QuizSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(answerRepository.findBySessionId(100)).thenReturn(List.of());

        final var detail = service.startSession(session.getToken());

        assertThat(detail.status()).isEqualTo("in_progress");
        assertThat(detail.startedAt()).isNotNull();
    }

    @Test
    void should_throw_when_starting_non_pending_session() {
        final var session = new QuizSession(quizTemplate, "Alice", "alice@example.com");
        session.setStatus(SessionStatus.IN_PROGRESS);

        when(sessionRepository.findByTokenWithQuizAndQuestions(session.getToken()))
                .thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.startSession(session.getToken()))
                .isInstanceOf(SessionStateException.class)
                .hasMessageContaining("in_progress");
    }

    @Test
    void should_submit_answer_and_auto_start_session() {
        final var session = new QuizSession(quizTemplate, "Alice", "alice@example.com");
        setField(session, "id", 100);

        when(sessionRepository.findByTokenWithQuizAndQuestions(session.getToken()))
                .thenReturn(Optional.of(session));
        when(answerRepository.findBySessionIdAndQuestionId(100, 10)).thenReturn(Optional.empty());
        when(answerRepository.save(any(QuizSessionAnswer.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        final var request = new SubmitAnswerRequest(10, "Ma réponse");
        service.submitAnswer(session.getToken(), request);

        assertThat(session.getStatus()).isEqualTo(SessionStatus.IN_PROGRESS);
        assertThat(session.getStartedAt()).isNotNull();
        verify(answerRepository).save(any(QuizSessionAnswer.class));
    }

    @Test
    void should_update_existing_answer() {
        final var session = new QuizSession(quizTemplate, "Alice", "alice@example.com");
        session.setStatus(SessionStatus.IN_PROGRESS);
        session.setStartedAt(OffsetDateTime.now());
        setField(session, "id", 100);

        final var existingAnswer = new QuizSessionAnswer(session, question, "Ancienne réponse");
        when(sessionRepository.findByTokenWithQuizAndQuestions(session.getToken()))
                .thenReturn(Optional.of(session));
        when(answerRepository.findBySessionIdAndQuestionId(100, 10))
                .thenReturn(Optional.of(existingAnswer));
        when(answerRepository.save(any(QuizSessionAnswer.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        final var request = new SubmitAnswerRequest(10, "Nouvelle réponse");
        service.submitAnswer(session.getToken(), request);

        assertThat(existingAnswer.getCandidateAnswer()).isEqualTo("Nouvelle réponse");
    }

    @Test
    void should_throw_when_submitting_to_completed_session() {
        final var session = new QuizSession(quizTemplate, "Alice", "alice@example.com");
        session.setStatus(SessionStatus.COMPLETED);

        when(sessionRepository.findByTokenWithQuizAndQuestions(session.getToken()))
                .thenReturn(Optional.of(session));

        final var request = new SubmitAnswerRequest(10, "Réponse tardive");

        assertThatThrownBy(() -> service.submitAnswer(session.getToken(), request))
                .isInstanceOf(SessionStateException.class)
                .hasMessageContaining("terminée");
    }

    @Test
    void should_complete_in_progress_session() {
        final var session = new QuizSession(quizTemplate, "Alice", "alice@example.com");
        session.setStatus(SessionStatus.IN_PROGRESS);
        session.setStartedAt(OffsetDateTime.now());
        setField(session, "id", 100);

        when(sessionRepository.findByTokenWithQuizAndQuestions(session.getToken()))
                .thenReturn(Optional.of(session));
        when(sessionRepository.save(any(QuizSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(answerRepository.findBySessionId(100)).thenReturn(List.of());

        final var detail = service.completeSession(session.getToken());

        assertThat(detail.status()).isEqualTo("completed");
        assertThat(detail.completedAt()).isNotNull();
    }

    @Test
    void should_throw_when_completing_non_in_progress_session() {
        final var session = new QuizSession(quizTemplate, "Alice", "alice@example.com");
        session.setStatus(SessionStatus.COMPLETED);

        when(sessionRepository.findByTokenWithQuizAndQuestions(session.getToken()))
                .thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.completeSession(session.getToken()))
                .isInstanceOf(SessionStateException.class)
                .hasMessageContaining("completed");
    }

    @Test
    void should_mask_expected_answers_when_session_not_completed() {
        final var session = new QuizSession(quizTemplate, "Alice", "alice@example.com");
        session.setStatus(SessionStatus.IN_PROGRESS);
        session.setStartedAt(OffsetDateTime.now());
        setField(session, "id", 100);

        final var answer = new QuizSessionAnswer(session, question, "Ma réponse sealed");
        when(sessionRepository.findByTokenWithQuizAndQuestions(session.getToken()))
                .thenReturn(Optional.of(session));
        when(answerRepository.findBySessionId(100)).thenReturn(List.of(answer));

        final var detail = service.getSessionByToken(session.getToken());

        assertThat(detail.sessionId()).isEqualTo(100);
        assertThat(detail.candidateName()).isEqualTo("Alice");
        assertThat(detail.quiz().title()).isEqualTo("Quiz Java");
        assertThat(detail.questions()).hasSize(1);

        final var questionDetail = detail.questions().getFirst();
        assertThat(questionDetail.question()).isEqualTo("Qu'est-ce qu'un sealed class ?");
        assertThat(questionDetail.candidateAnswer()).isEqualTo("Ma réponse sealed");
        assertThat(questionDetail.technology()).isEqualTo("Java");
        assertThat(questionDetail.targetVersion()).isEqualTo("21");
        assertThat(questionDetail.answerType()).isEqualTo("code");
        // Les réponses attendues sont masquées tant que la session n'est pas terminée
        assertThat(questionDetail.expectedAnswer()).isNull();
        assertThat(questionDetail.explanation()).isNull();
    }

    @Test
    void should_reveal_expected_answers_when_session_completed() {
        final var session = new QuizSession(quizTemplate, "Alice", "alice@example.com");
        session.setStatus(SessionStatus.COMPLETED);
        session.setStartedAt(OffsetDateTime.now());
        session.setCompletedAt(OffsetDateTime.now());
        setField(session, "id", 100);

        final var answer = new QuizSessionAnswer(session, question, "Ma réponse sealed");
        when(sessionRepository.findByTokenWithQuizAndQuestions(session.getToken()))
                .thenReturn(Optional.of(session));
        when(answerRepository.findBySessionId(100)).thenReturn(List.of(answer));

        final var detail = service.getSessionByToken(session.getToken());

        final var questionDetail = detail.questions().getFirst();
        // Les réponses attendues sont visibles une fois la session terminée
        assertThat(questionDetail.expectedAnswer()).isEqualTo("Une classe restreinte dans ses sous-types");
        assertThat(questionDetail.explanation()).isEqualTo("Introduit en Java 17");
    }

    @Test
    void should_throw_when_session_not_found() {
        when(sessionRepository.findByTokenWithQuizAndQuestions("unknown-token"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSessionByToken("unknown-token"))
                .isInstanceOf(SessionNotFoundException.class)
                .hasMessageContaining("introuvable");
    }

    @Test
    void should_throw_when_submitting_answer_for_unknown_question() {
        final var session = new QuizSession(quizTemplate, "Alice", "alice@example.com");
        session.setStatus(SessionStatus.IN_PROGRESS);
        setField(session, "id", 100);

        when(sessionRepository.findByTokenWithQuizAndQuestions(session.getToken()))
                .thenReturn(Optional.of(session));
        when(answerRepository.findBySessionIdAndQuestionId(100, 999)).thenReturn(Optional.empty());

        final var request = new SubmitAnswerRequest(999, "Réponse");

        assertThatThrownBy(() -> service.submitAnswer(session.getToken(), request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("999");
    }

    @Test
    void should_throw_when_completing_pending_session() {
        final var session = new QuizSession(quizTemplate, "Alice", "alice@example.com");
        // Le statut est "pending" par défaut

        when(sessionRepository.findByTokenWithQuizAndQuestions(session.getToken()))
                .thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.completeSession(session.getToken()))
                .isInstanceOf(SessionStateException.class)
                .hasMessageContaining("pending");
    }

    @Test
    void should_auto_complete_expired_session_on_get() {
        final var session = new QuizSession(quizTemplate, "Alice", "alice@example.com");
        session.setStatus(SessionStatus.IN_PROGRESS);
        // Simuler un démarrage il y a 2 heures (le quiz dure 30 minutes)
        session.setStartedAt(OffsetDateTime.now().minusHours(2));
        setField(session, "id", 100);

        when(sessionRepository.findByTokenWithQuizAndQuestions(session.getToken()))
                .thenReturn(Optional.of(session));
        when(sessionRepository.save(any(QuizSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(answerRepository.findBySessionId(100)).thenReturn(List.of());

        final var detail = service.getSessionByToken(session.getToken());

        assertThat(detail.status()).isEqualTo("completed");
        assertThat(session.getCompletedAt()).isNotNull();
        verify(sessionRepository).save(any(QuizSession.class));
    }

    @Test
    void should_reject_answer_on_expired_session() {
        final var session = new QuizSession(quizTemplate, "Alice", "alice@example.com");
        session.setStatus(SessionStatus.IN_PROGRESS);
        session.setStartedAt(OffsetDateTime.now().minusHours(2));
        setField(session, "id", 100);

        when(sessionRepository.findByTokenWithQuizAndQuestions(session.getToken()))
                .thenReturn(Optional.of(session));
        when(sessionRepository.save(any(QuizSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        final var request = new SubmitAnswerRequest(10, "Réponse tardive");

        assertThatThrownBy(() -> service.submitAnswer(session.getToken(), request))
                .isInstanceOf(SessionStateException.class)
                .hasMessageContaining("terminée");
    }

}
