package com.saii.quizapi.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Requête combinée : matching de quiz + création de session candidat.
 * Le progiciel RH envoie les prérequis de l'offre et les infos du candidat
 * en un seul appel, et reçoit directement l'URL de la session.
 */
public record MatchAndStartRequest(
        @NotBlank @Size(max = 500) String jobTitle,
        @NotEmpty @Valid List<TechPrerequisite> prerequisites,
        @Positive Integer maxQuestions,
        @NotBlank @Size(max = 255) String candidateName,
        @NotBlank @Email @Size(max = 255) String candidateEmail
) {
    private static final int DEFAULT_MAX_QUESTIONS = 20;

    public int effectiveMaxQuestions() {
        return maxQuestions != null ? maxQuestions : DEFAULT_MAX_QUESTIONS;
    }
}
