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
 * <p>
 * Les champs de matching (jobTitle, prerequisites, maxQuestions) sont dupliqués
 * volontairement (limitations des records + validation Jakarta).
 * La méthode {@link #toMatchRequest()} fournit la conversion sans duplication de logique.
 */
public record MatchAndStartRequest(
        @NotBlank @Size(max = 500) String jobTitle,
        @NotEmpty @Valid List<TechPrerequisite> prerequisites,
        @Positive Integer maxQuestions,
        @NotBlank @Size(max = 255) String candidateName,
        @NotBlank @Email @Size(max = 255) String candidateEmail
) {
    /**
     * Convertit la partie matching en {@link MatchRequest},
     * évitant la duplication de logique (effectiveMaxQuestions, etc.).
     */
    public MatchRequest toMatchRequest() {
        return new MatchRequest(jobTitle, prerequisites, maxQuestions);
    }
}
