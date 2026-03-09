package com.saii.quizapi.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Réponse JSON représentant un quiz complet.
 */
public record QuizResponseDTO(
        int id,
        String title,
        String description,
        String targetSeniority,
        int durationMinutes,
        String createdBy,
        OffsetDateTime createdAt,
        List<QuizQuestionDTO> questions
) {
}
