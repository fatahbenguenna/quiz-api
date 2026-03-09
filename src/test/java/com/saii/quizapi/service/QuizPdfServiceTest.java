package com.saii.quizapi.service;

import com.saii.quizapi.dto.QuizQuestionDto;
import com.saii.quizapi.dto.QuizResponse;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QuizPdfServiceTest {

    private final QuizPdfService service = new QuizPdfService();

    @Test
    void should_generate_valid_pdf_bytes() {
        // Given
        final var quiz = new QuizResponse(
                1,
                "Quiz Java Confirmé",
                "Quiz de test pour le service PDF",
                "confirme",
                30,
                "java-matcher",
                OffsetDateTime.now(),
                List.of(
                        new QuizQuestionDto(
                                1, "Java", "confirme", "21",
                                "Qu'est-ce que le polymorphisme ?",
                                "Le polymorphisme permet à un objet de prendre plusieurs formes.",
                                "Concept fondamental de la POO.",
                                (short) 3,
                                "code"
                        ),
                        new QuizQuestionDto(
                                2, "Spring Boot", "confirme", "3.2",
                                "Quel est le rôle de @SpringBootApplication ?",
                                "Combine @Configuration, @EnableAutoConfiguration et @ComponentScan.",
                                null,
                                (short) 2,
                                "text"
                        )
                )
        );

        // When
        final var pdfBytes = service.generate(quiz);

        // Then — vérifier le magic number PDF
        assertThat(pdfBytes).isNotEmpty();
        assertThat(pdfBytes.length).isGreaterThan(100);
        // Les PDFs commencent par %PDF
        assertThat(new String(pdfBytes, 0, 5)).startsWith("%PDF");
    }

    @Test
    void should_generate_pdf_with_empty_questions() {
        // Given
        final var quiz = new QuizResponse(
                2, "Quiz vide", null, "junior",
                15, "ai", OffsetDateTime.now(), List.of()
        );

        // When
        final var pdfBytes = service.generate(quiz);

        // Then
        assertThat(pdfBytes).isNotEmpty();
        assertThat(new String(pdfBytes, 0, 5)).startsWith("%PDF");
    }
}
