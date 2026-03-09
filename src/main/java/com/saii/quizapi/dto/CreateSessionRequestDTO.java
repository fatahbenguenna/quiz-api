package com.saii.quizapi.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Requete de creation de session de quiz.
 * Peut etre appelee directement avec un quizId,
 * ou indirectement via /match qui cree le quiz puis la session.
 */
public record CreateSessionRequestDTO(
        @NotNull Integer quizId,
        @NotBlank @Size(max = 255) String candidateName,
        @NotBlank @Email @Size(max = 255) String candidateEmail
) {
}
