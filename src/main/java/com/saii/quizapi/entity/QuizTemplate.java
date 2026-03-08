package com.saii.quizapi.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "quiz_templates")
@Getter
@NoArgsConstructor
public class QuizTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 500)
    @Setter
    private String title;

    @Column(columnDefinition = "text")
    @Setter
    private String description;

    @Column(name = "target_seniority", nullable = false, length = 20)
    @Setter
    private String targetSeniority;

    @Column(name = "duration_minutes", nullable = false)
    @Setter
    private Integer durationMinutes;

    @Column(name = "created_by", nullable = false, length = 20)
    @Setter
    private String createdBy;

    @Column(name = "source_offer_id")
    @Setter
    private Integer sourceOfferId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @OneToMany(mappedBy = "quizTemplate", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<QuizTemplateQuestion> quizQuestions = new ArrayList<>();

    public QuizTemplate(final String title,
                        final String description,
                        final String targetSeniority,
                        final int durationMinutes) {
        this.title = title;
        this.description = description;
        this.targetSeniority = targetSeniority;
        this.durationMinutes = durationMinutes;
        this.createdBy = "java-matcher";
        this.createdAt = OffsetDateTime.now();
    }

    public void addQuestion(final Question question, final short position) {
        final var link = new QuizTemplateQuestion(this, question, position);
        this.quizQuestions.add(link);
    }
}
