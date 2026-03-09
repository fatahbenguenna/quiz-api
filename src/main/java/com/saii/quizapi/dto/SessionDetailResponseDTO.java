package com.saii.quizapi.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Detail complet d'une session : quiz + questions + reponses candidat.
 * Utilisee par l'app standalone pour afficher le quiz au candidat
 * et la revue a l'interviewer.
 */
public record SessionDetailResponseDTO(
        int sessionId,
        String token,
        String status,
        String candidateName,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        QuizInfo quiz,
        List<SessionQuestionResponse> questions
) {
    public record QuizInfo(
            int id,
            String title,
            String description,
            String targetSeniority,
            int durationMinutes
    ) {
    }

    public record SessionQuestionResponse(
            int questionId,
            int position,
            String technology,
            String targetVersion,
            String seniorityLevel,
            String question,
            String expectedAnswer,
            String explanation,
            short difficultyScore,
            String answerType,
            String candidateAnswer
    ) {
    }
}
