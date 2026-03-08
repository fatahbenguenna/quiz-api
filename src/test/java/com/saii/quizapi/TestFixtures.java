package com.saii.quizapi;

import com.saii.quizapi.entity.Question;
import com.saii.quizapi.entity.QuizTemplate;
import com.saii.quizapi.entity.SeniorityLevel;
import com.saii.quizapi.entity.Technology;

/**
 * Usine à objets de test pour les entités JPA (qui n'ont pas de setters sur les IDs).
 * Centralise les helpers de réflexion et la création de fixtures.
 */
public final class TestFixtures {

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
        final var q = new Question();
        setField(q, "id", id);
        setField(q, "technology", tech);
        setField(q, "seniorityLevel", seniority);
        setField(q, "question", questionText);
        setField(q, "answer", "Réponse de test");
        setField(q, "difficultyScore", (short) 3);
        setField(q, "targetVersion", null);
        return q;
    }

    public static Question createQuestionFull(final int id, final Technology tech,
                                               final String seniority, final String questionText,
                                               final String answer, final String explanation,
                                               final short difficulty, final String targetVersion) {
        final var q = new Question();
        setField(q, "id", id);
        setField(q, "technology", tech);
        setField(q, "seniorityLevel", seniority);
        setField(q, "question", questionText);
        setField(q, "answer", answer);
        setField(q, "explanation", explanation);
        setField(q, "difficultyScore", difficulty);
        setField(q, "targetVersion", targetVersion);
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
        final var qt = new QuizTemplate(title, "Description test", targetSeniority, durationMinutes);
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
}
