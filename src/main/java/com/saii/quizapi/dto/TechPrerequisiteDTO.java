package com.saii.quizapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Prérequis technique envoyé par le progiciel RH.
 *
 * @param technology nom de la technologie (ex: "Java", "Spring Boot")
 * @param seniority  niveau de séniorité attendu (ex: "confirme", "senior")
 */
public record TechPrerequisiteDTO(
        @NotBlank @Size(max = 100) String technology,
        @NotBlank @Size(max = 20) String seniority
) {
}
