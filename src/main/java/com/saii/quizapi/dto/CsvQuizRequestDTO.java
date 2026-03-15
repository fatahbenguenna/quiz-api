package com.saii.quizapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Requête pour générer un PDF de quiz à partir des technologies CSV.
 *
 * @param technologies texte brut avec les noms de technologies séparés par des virgules
 *                     (ex: "Java, Spring Boot, PostgreSQL")
 * @param seniority    niveau de séniorité cible (ex: "junior", "confirme", "senior")
 */
public record CsvQuizRequestDTO(
        @NotBlank(message = "La liste de technologies est obligatoire")
        @Size(max = 2000, message = "La liste de technologies ne doit pas dépasser 2000 caractères")
        String technologies,

        @NotBlank(message = "Le niveau de séniorité est obligatoire")
        @Size(max = 20, message = "Le niveau de séniorité ne doit pas dépasser 20 caractères")
        String seniority
) {
}
