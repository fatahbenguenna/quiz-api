package com.saii.quizapi.dto;

/**
 * Question d'un quiz — vue simplifiée pour le JSON / PDF.
 */
public record QuizQuestionDTO(
        int position,
        String technology,
        String seniorityLevel,
        String targetVersion,
        String question,
        String answer,
        String explanation,
        short difficultyScore,
        String answerType
) {
}
