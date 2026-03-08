package com.saii.quizapi.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Requête de matching envoyée par le progiciel RH.
 * Contient le titre du poste et la liste des prérequis techniques.
 *
 * @param jobTitle      intitulé du poste (utilisé comme titre du quiz)
 * @param prerequisites liste des prérequis techniques
 * @param maxQuestions  nombre max de questions souhaitées (défaut : 20)
 */
public record MatchRequest(
        @NotBlank @Size(max = 500) String jobTitle,
        @NotEmpty @Valid List<TechPrerequisite> prerequisites,
        @Positive Integer maxQuestions
) {
    private static final int DEFAULT_MAX_QUESTIONS = 20;

    public int effectiveMaxQuestions() {
        return maxQuestions != null ? maxQuestions : DEFAULT_MAX_QUESTIONS;
    }
}
