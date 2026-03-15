package com.saii.quizapi.service;

import com.saii.quizapi.dto.CsvQuizRequestDTO;
import com.saii.quizapi.repository.QuestionCsvRepository;
import com.saii.quizapi.repository.TechnologyCsvRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import static com.saii.quizapi.TestFixtures.TEST_NOW;
import static com.saii.quizapi.TestFixtures.createQuestionCsv;
import static com.saii.quizapi.TestFixtures.createTechnologyCsv;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CsvQuizServiceTest {

    @Mock
    private TechnologyCsvRepository technologyCsvRepository;

    @Mock
    private QuestionCsvRepository questionCsvRepository;

    private CsvQuizService service;

    @BeforeEach
    void setUp() {
        final var fixedClock = Clock.fixed(TEST_NOW.toInstant(), TEST_NOW.getOffset());
        service = new CsvQuizService(technologyCsvRepository, questionCsvRepository, fixedClock);
    }

    @Test
    void should_parse_technology_names_from_comma_separated_text() {
        final var result = service.parseTechnologyNames("Java, Spring Boot, PostgreSQL");

        assertThat(result).containsExactly("Java", "Spring Boot", "PostgreSQL");
    }

    @Test
    void should_parse_technology_names_from_semicolon_separated_text() {
        final var result = service.parseTechnologyNames("Java;Spring Boot;PostgreSQL");

        assertThat(result).containsExactly("Java", "Spring Boot", "PostgreSQL");
    }

    @Test
    void should_throw_when_no_technology_found() {
        when(technologyCsvRepository.findByNameIgnoreCase("Kotlin")).thenReturn(Optional.empty());

        final var request = new CsvQuizRequestDTO("Kotlin", "senior");

        assertThatThrownBy(() -> service.assembleFromCsv(request))
                .isInstanceOf(QuizNotFoundException.class)
                .hasMessageContaining("Aucune technologie trouvée");
    }

    @Test
    void should_throw_when_no_questions_found() {
        final var tech = createTechnologyCsv(1, "Java");
        when(technologyCsvRepository.findByNameIgnoreCase("Java")).thenReturn(Optional.of(tech));
        when(questionCsvRepository.findByTechnologyIdsAndSeniority(List.of(1), "expert"))
                .thenReturn(List.of());
        when(questionCsvRepository.findByTechnologyId(1)).thenReturn(List.of());

        final var request = new CsvQuizRequestDTO("Java", "expert");

        assertThatThrownBy(() -> service.assembleFromCsv(request))
                .isInstanceOf(QuizNotFoundException.class)
                .hasMessageContaining("Aucune question CSV");
    }

    @Test
    void should_assemble_quiz_with_3_questions_per_technology() {
        final var techJava = createTechnologyCsv(1, "Java");
        final var techSpring = createTechnologyCsv(2, "Spring Boot");

        when(technologyCsvRepository.findByNameIgnoreCase("Java")).thenReturn(Optional.of(techJava));
        when(technologyCsvRepository.findByNameIgnoreCase("Spring Boot")).thenReturn(Optional.of(techSpring));

        final var javaQuestions = List.of(
                createQuestionCsv(10, techJava, "confirme", "Q Java 1"),
                createQuestionCsv(11, techJava, "confirme", "Q Java 2"),
                createQuestionCsv(12, techJava, "confirme", "Q Java 3"),
                createQuestionCsv(13, techJava, "confirme", "Q Java 4")
        );
        final var springQuestions = List.of(
                createQuestionCsv(20, techSpring, "confirme", "Q Spring 1"),
                createQuestionCsv(21, techSpring, "confirme", "Q Spring 2")
        );

        final var allQuestions = new java.util.ArrayList<>(javaQuestions);
        allQuestions.addAll(springQuestions);

        when(questionCsvRepository.findByTechnologyIdsAndSeniority(List.of(1, 2), "confirme"))
                .thenReturn(allQuestions);

        final var request = new CsvQuizRequestDTO("Java, Spring Boot", "confirme");
        final var result = service.assembleFromCsv(request);

        assertThat(result.targetSeniority()).isEqualTo("confirme");
        assertThat(result.createdBy()).isEqualTo(CsvQuizService.CREATED_BY);
        // 3 questions Java (limité) + 2 questions Spring (< 3 dispo)
        assertThat(result.questions()).hasSize(5);
        assertThat(result.questions().stream().filter(q -> q.technology().equals("Java")).count())
                .isEqualTo(3);
        assertThat(result.questions().stream().filter(q -> q.technology().equals("Spring Boot")).count())
                .isEqualTo(2);
    }

    @Test
    void should_fallback_to_all_seniority_levels_when_none_match() {
        final var tech = createTechnologyCsv(1, "Docker");
        when(technologyCsvRepository.findByNameIgnoreCase("Docker")).thenReturn(Optional.of(tech));
        when(questionCsvRepository.findByTechnologyIdsAndSeniority(List.of(1), "architecte"))
                .thenReturn(List.of());
        when(questionCsvRepository.findByTechnologyId(1))
                .thenReturn(List.of(createQuestionCsv(30, tech, "junior", "Q Docker")));

        final var request = new CsvQuizRequestDTO("Docker", "architecte");
        final var result = service.assembleFromCsv(request);

        assertThat(result.questions()).hasSize(1);
        assertThat(result.questions().getFirst().technology()).isEqualTo("Docker");
    }

    @Test
    void should_set_correct_positions_in_questions() {
        final var tech = createTechnologyCsv(1, "Kafka");
        when(technologyCsvRepository.findByNameIgnoreCase("Kafka")).thenReturn(Optional.of(tech));
        when(questionCsvRepository.findByTechnologyIdsAndSeniority(List.of(1), "senior"))
                .thenReturn(List.of(
                        createQuestionCsv(40, tech, "senior", "Q1"),
                        createQuestionCsv(41, tech, "senior", "Q2")
                ));

        final var request = new CsvQuizRequestDTO("Kafka", "senior");
        final var result = service.assembleFromCsv(request);

        assertThat(result.questions().get(0).position()).isEqualTo(1);
        assertThat(result.questions().get(1).position()).isEqualTo(2);
    }

    @Test
    void should_skip_unknown_technologies_and_keep_known_ones() {
        final var tech = createTechnologyCsv(1, "Java");
        when(technologyCsvRepository.findByNameIgnoreCase("Java")).thenReturn(Optional.of(tech));
        when(technologyCsvRepository.findByNameIgnoreCase("InconnuTech")).thenReturn(Optional.empty());
        when(questionCsvRepository.findByTechnologyIdsAndSeniority(List.of(1), "junior"))
                .thenReturn(List.of(createQuestionCsv(50, tech, "junior", "Q Java")));

        final var request = new CsvQuizRequestDTO("Java, InconnuTech", "junior");
        final var result = service.assembleFromCsv(request);

        assertThat(result.questions()).hasSize(1);
        assertThat(result.title()).contains("Java");
    }
}
