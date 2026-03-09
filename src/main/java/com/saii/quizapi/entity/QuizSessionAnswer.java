package com.saii.quizapi.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Entity
@Table(name = "quiz_session_answers")
@Getter
@NoArgsConstructor
public class QuizSessionAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private QuizSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(name = "candidate_answer", columnDefinition = "text")
    private String candidateAnswer;

    @Column(name = "submitted_at", nullable = false)
    private OffsetDateTime submittedAt;

    public QuizSessionAnswer(final QuizSession session,
                             final Question question,
                             final String candidateAnswer,
                             final OffsetDateTime now) {
        this.session = session;
        this.question = question;
        this.candidateAnswer = candidateAnswer;
        this.submittedAt = now;
    }

    public void updateAnswer(final String candidateAnswer) {
        this.candidateAnswer = candidateAnswer;
    }
}
