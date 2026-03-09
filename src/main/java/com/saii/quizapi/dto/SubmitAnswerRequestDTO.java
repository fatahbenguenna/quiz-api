package com.saii.quizapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record SubmitAnswerRequestDTO(
        @Positive int questionId,
        @NotBlank @Size(max = 50000) String candidateAnswer
) {
}
