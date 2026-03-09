package com.saii.quizapi;

import com.saii.quizapi.entity.AnswerType;
import com.saii.quizapi.entity.Question;
import com.saii.quizapi.entity.QuizTemplate;
import com.saii.quizapi.entity.SeniorityLevel;
import com.saii.quizapi.entity.Technology;

import java.time.OffsetDateTime;

/**
 * Usine à objets de test pour les entités JPA (qui n'ont pas de setters sur les IDs).
 * Centralise les helpers de réflexion et la création de fixtures.
 */
public final class TestFixtures {

    /** Instant fixe pour les tests — évite les dépendances temporelles */
    public static final OffsetDateTime TEST_NOW = OffsetDateTime.parse("2026-01-15T10:00:00+01:00");

    private TestFixtures() {
    }

    public static Technology createTechnology(final int id, final String name) {
        final var tech = new Technology();
        setField(tech, "id", id);
        setField(tech, "name", name);
        return tech;
    }

    public static Question createQuestion(final int id, final Technology tech,
                                           final String seniority, final String questionText) {
        return createQuestionFull(new QuestionParams(id, tech, seniority, questionText,
                "Réponse de test", null, (short) 3, null, AnswerType.CODE));
    }

    /**
     * Crée une question complète à partir d'un objet de paramètres typé.
     * Élimine le problème des méthodes à 9+ paramètres.
     */
    public static Question createQuestionFull(final QuestionParams params) {
        final var q = new Question();
        setField(q, "id", params.id());
        setField(q, "technology", params.technology());
        setField(q, "seniorityLevel", params.seniority());
        setField(q, "question", params.questionText());
        setField(q, "answer", params.answer());
        setField(q, "explanation", params.explanation());
        setField(q, "difficultyScore", params.difficulty());
        setField(q, "targetVersion", params.targetVersion());
        setField(q, "answerType", params.answerType());
        return q;
    }

    public static SeniorityLevel createSeniorityLevel(final String code, final short rank) {
        final var sl = new SeniorityLevel();
        setField(sl, "code", code);
        setField(sl, "rank", rank);
        return sl;
    }

    public static QuizTemplate createQuizTemplate(final int id, final String title,
                                                    final String targetSeniority, final int durationMinutes) {
        final var qt = new QuizTemplate(title, "Description test", targetSeniority,
                durationMinutes, "test-runner", TEST_NOW);
        setField(qt, "id", id);
        return qt;
    }

    /**
     * Injecte une valeur dans un champ privé via réflexion.
     * Remonte la hiérarchie de classes pour trouver le champ.
     */
    public static void setField(final Object target, final String fieldName, final Object value) {
        try {
            var clazz = target.getClass();
            while (clazz != null) {
                try {
                    final var field = clazz.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    field.set(target, value);
                    return;
                } catch (final NoSuchFieldException e) {
                    clazz = clazz.getSuperclass();
                }
            }
            throw new NoSuchFieldException(fieldName + " introuvable dans " + target.getClass().getName());
        } catch (final ReflectiveOperationException e) {
            throw new RuntimeException("Impossible d'injecter le champ " + fieldName, e);
        }
    }

    /**
     * Record de paramètres pour la création de questions de test.
     * Remplace les méthodes à 9 paramètres positionnels.
     */
    public record QuestionParams(
            int id,
            Technology technology,
            String seniority,
            String questionText,
            String answer,
            String explanation,
            short difficulty,
            String targetVersion,
            AnswerType answerType
    ) {
    }
}
