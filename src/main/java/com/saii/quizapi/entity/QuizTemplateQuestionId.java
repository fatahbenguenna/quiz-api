package com.saii.quizapi.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;

/**
 * Clé composite pour la table de jointure quiz_template_questions.
 * Utilise un record Java 21 : equals/hashCode/toString générés automatiquement.
 */
@Embeddable
public record QuizTemplateQuestionId(
        @Column(name = "quiz_template_id") Integer quizTemplateId,
        @Column(name = "question_id") Integer questionId
) implements Serializable {

    /** Constructeur sans argument requis par JPA. */
    public QuizTemplateQuestionId() {
        this(null, null);
    }
}
