package com.saii.quizapi.entity;

import com.saii.quizapi.service.SessionStateException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Entité JPA représentant une session de quiz candidat.
 * Encapsule la machine à états : pending → in_progress → completed.
 * Les transitions sont validées dans les méthodes métier (start, complete).
 */
@Entity
@Table(name = "quiz_sessions")
@Getter
@NoArgsConstructor
public class QuizSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true, length = 100)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quiz_template_id", nullable = false)
    private QuizTemplate quizTemplate;

    @Column(name = "candidate_name", length = 255)
    private String candidateName;

    @Column(name = "candidate_email", length = 255)
    private String candidateEmail;

    @Column(nullable = false, length = 20)
    private SessionStatus status;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("submittedAt ASC")
    private List<QuizSessionAnswer> answers = new ArrayList<>();

    public QuizSession(final QuizTemplate quizTemplate,
                       final String candidateName,
                       final String candidateEmail,
                       final OffsetDateTime now) {
        this.token = UUID.randomUUID().toString();
        this.quizTemplate = quizTemplate;
        this.candidateName = candidateName;
        this.candidateEmail = candidateEmail;
        this.status = SessionStatus.PENDING;
        this.createdAt = now;
    }

    /**
     * Démarre la session : PENDING → IN_PROGRESS.
     *
     * @throws SessionStateException si la session n'est pas en statut PENDING
     */
    public void start(final OffsetDateTime now) {
        if (this.status != SessionStatus.PENDING) {
            throw new SessionStateException(
                    "Impossible de démarrer la session : état actuel = " + this.status.getValue());
        }
        this.status = SessionStatus.IN_PROGRESS;
        this.startedAt = now;
    }

    /**
     * Termine la session : IN_PROGRESS → COMPLETED.
     *
     * @throws SessionStateException si la session n'est pas en statut IN_PROGRESS
     */
    public void complete(final OffsetDateTime now) {
        if (this.status != SessionStatus.IN_PROGRESS) {
            throw new SessionStateException(
                    "Impossible de terminer la session : état actuel = " + this.status.getValue());
        }
        this.status = SessionStatus.COMPLETED;
        this.completedAt = now;
    }

    /**
     * Auto-complète la session si le temps imparti (durationMinutes) est dépassé.
     * Retourne true si la session a été complétée, false sinon.
     */
    public boolean autoCompleteIfExpired(final OffsetDateTime now) {
        if (this.status != SessionStatus.IN_PROGRESS || this.startedAt == null) {
            return false;
        }

        final var durationMinutes = this.quizTemplate.getDurationMinutes();
        final var deadline = this.startedAt.plusMinutes(durationMinutes);

        if (now.isAfter(deadline)) {
            this.status = SessionStatus.COMPLETED;
            this.completedAt = deadline;
            return true;
        }
        return false;
    }

    /**
     * Vérifie que la session accepte des réponses (pas COMPLETED).
     * Auto-démarre si la session est en PENDING.
     *
     * @throws SessionStateException si la session est terminée
     */
    public void ensureAcceptsAnswers(final OffsetDateTime now) {
        if (this.status == SessionStatus.COMPLETED) {
            throw new SessionStateException("Impossible de soumettre : la session est terminée");
        }
        if (this.status == SessionStatus.PENDING) {
            this.status = SessionStatus.IN_PROGRESS;
            this.startedAt = now;
        }
    }

    public boolean isCompleted() {
        return this.status == SessionStatus.COMPLETED;
    }
}
