package com.saii.quizapi.entity;

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
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
    @Setter
    private String candidateName;

    @Column(name = "candidate_email", length = 255)
    @Setter
    private String candidateEmail;

    @Column(nullable = false, length = 20)
    @Setter
    private SessionStatus status;

    @Column(name = "started_at")
    @Setter
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    @Setter
    private OffsetDateTime completedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("submittedAt ASC")
    private List<QuizSessionAnswer> answers = new ArrayList<>();

    public QuizSession(final QuizTemplate quizTemplate,
                       final String candidateName,
                       final String candidateEmail) {
        this.token = UUID.randomUUID().toString();
        this.quizTemplate = quizTemplate;
        this.candidateName = candidateName;
        this.candidateEmail = candidateEmail;
        this.status = SessionStatus.PENDING;
        this.createdAt = OffsetDateTime.now();
    }
}
