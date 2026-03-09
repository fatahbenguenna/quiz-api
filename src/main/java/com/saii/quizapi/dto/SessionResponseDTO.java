package com.saii.quizapi.dto;

import java.time.OffsetDateTime;

/**
 * Reponse apres creation d'une session — contient le token d'acces.
 */
public record SessionResponseDTO(
        int id,
        String token,
        String sessionUrl,
        int quizId,
        String quizTitle,
        String candidateName,
        String status,
        OffsetDateTime createdAt
) {
}
