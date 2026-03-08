package com.saii.quizapi.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "quiz_template_questions")
@Getter
@NoArgsConstructor
public class QuizTemplateQuestion {

    @EmbeddedId
    private QuizTemplateQuestionId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("quizTemplateId")
    @JoinColumn(name = "quiz_template_id")
    private QuizTemplate quizTemplate;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("questionId")
    @JoinColumn(name = "question_id")
    private Question question;

    private short position;

    public QuizTemplateQuestion(final QuizTemplate quizTemplate,
                                final Question question,
                                final short position) {
        this.id = new QuizTemplateQuestionId(quizTemplate.getId(), question.getId());
        this.quizTemplate = quizTemplate;
        this.question = question;
        this.position = position;
    }
}
