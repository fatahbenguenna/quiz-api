package com.saii.quizapi.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Embeddable
@Getter
@NoArgsConstructor
@EqualsAndHashCode
public class QuizTemplateQuestionId implements Serializable {

    @Column(name = "quiz_template_id")
    private Integer quizTemplateId;

    @Column(name = "question_id")
    private Integer questionId;

    public QuizTemplateQuestionId(final Integer quizTemplateId, final Integer questionId) {
        this.quizTemplateId = quizTemplateId;
        this.questionId = questionId;
    }
}
