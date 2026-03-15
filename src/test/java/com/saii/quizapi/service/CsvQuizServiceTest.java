package com.saii.quizapi.service;

import com.saii.quizapi.dto.CsvQuizRequestDTO;
import com.saii.quizapi.entity.TechnologyCsv;
import com.saii.quizapi.repository.QuestionCsvRepository;
import com.saii.quizapi.repository.TechnologyCsvRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.util.List;

import static com.saii.quizapi.TestFixtures.TEST_NOW;
import static com.saii.quizapi.TestFixtures.createQuestionCsv;
import static com.saii.quizapi.TestFixtures.createTechnologyCsv;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
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

    // -------------------------------------------------------------------------
    // Découverte des technologies dans le texte
    // -------------------------------------------------------------------------
    @Nested
    class DiscoverTechnologies {

        @Test
        void should_find_technologies_with_comma_separators() {
            final var allTechs = List.of(
                    createTechnologyCsv(1, "Java"),
                    createTechnologyCsv(2, "Spring Boot"),
                    createTechnologyCsv(3, "PostgreSQL")
            );
            when(technologyCsvRepository.findAll()).thenReturn(allTechs);

            final var result = service.discoverTechnologies("Java, Spring Boot, PostgreSQL");

            assertThat(result).extracting(TechnologyCsv::getName)
                    .containsExactlyInAnyOrder("Java", "Spring Boot", "PostgreSQL");
        }

        @Test
        void should_find_technologies_without_separators() {
            final var allTechs = List.of(
                    createTechnologyCsv(1, "Java"),
                    createTechnologyCsv(2, "Spring Boot"),
                    createTechnologyCsv(3, "PostgreSQL")
            );
            when(technologyCsvRepository.findAll()).thenReturn(allTechs);

            final var result = service.discoverTechnologies("Java Spring Boot PostgreSQL");

            assertThat(result).extracting(TechnologyCsv::getName)
                    .containsExactlyInAnyOrder("Java", "Spring Boot", "PostgreSQL");
        }

        @Test
        void should_prefer_longest_match_over_shorter() {
            final var allTechs = List.of(
                    createTechnologyCsv(1, "Spring"),
                    createTechnologyCsv(2, "Spring Boot"),
                    createTechnologyCsv(3, "Spring Data")
            );
            when(technologyCsvRepository.findAll()).thenReturn(allTechs);

            final var result = service.discoverTechnologies("Spring Boot Spring Data");

            assertThat(result).extracting(TechnologyCsv::getName)
                    .containsExactlyInAnyOrder("Spring Boot", "Spring Data");
            // "Spring" seul ne doit pas matcher car consommé par les matchs plus longs
        }

        @Test
        void should_match_case_insensitively() {
            final var allTechs = List.of(
                    createTechnologyCsv(1, "Java"),
                    createTechnologyCsv(2, "PostgreSQL")
            );
            when(technologyCsvRepository.findAll()).thenReturn(allTechs);

            final var result = service.discoverTechnologies("java postgresql");

            assertThat(result).extracting(TechnologyCsv::getName)
                    .containsExactlyInAnyOrder("Java", "PostgreSQL");
        }

        @Test
        void should_not_duplicate_technologies() {
            final var allTechs = List.of(
                    createTechnologyCsv(1, "Java")
            );
            when(technologyCsvRepository.findAll()).thenReturn(allTechs);

            final var result = service.discoverTechnologies("Java, Java, java");

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().getName()).isEqualTo("Java");
        }

        @Test
        void should_return_empty_when_no_match() {
            final var allTechs = List.of(
                    createTechnologyCsv(1, "Java"),
                    createTechnologyCsv(2, "Kotlin")
            );
            when(technologyCsvRepository.findAll()).thenReturn(allTechs);

            final var result = service.discoverTechnologies("Python Ruby Go");

            assertThat(result).isEmpty();
        }

        @Test
        void should_handle_mixed_separators_and_no_separators() {
            final var allTechs = List.of(
                    createTechnologyCsv(1, "Java"),
                    createTechnologyCsv(2, "Kafka"),
                    createTechnologyCsv(3, "Docker"),
                    createTechnologyCsv(4, "Kubernetes")
            );
            when(technologyCsvRepository.findAll()).thenReturn(allTechs);

            final var result = service.discoverTechnologies("Java, Kafka Docker Kubernetes");

            assertThat(result).extracting(TechnologyCsv::getName)
                    .containsExactlyInAnyOrder("Java", "Kafka", "Docker", "Kubernetes");
        }

        @Test
        void should_find_spring_alone_when_spring_boot_not_in_text() {
            final var allTechs = List.of(
                    createTechnologyCsv(1, "Spring"),
                    createTechnologyCsv(2, "Spring Boot")
            );
            when(technologyCsvRepository.findAll()).thenReturn(allTechs);

            final var result = service.discoverTechnologies("Spring");

            assertThat(result).extracting(TechnologyCsv::getName)
                    .containsExactly("Spring");
        }

        @Test
        void should_handle_technologies_with_special_characters() {
            final var allTechs = List.of(
                    createTechnologyCsv(1, "C++"),
                    createTechnologyCsv(2, "C#"),
                    createTechnologyCsv(3, ".NET")
            );
            when(technologyCsvRepository.findAll()).thenReturn(allTechs);

            final var result = service.discoverTechnologies("C++ C# .NET");

            assertThat(result).extracting(TechnologyCsv::getName)
                    .containsExactlyInAnyOrder("C++", "C#", ".NET");
        }
    }

    // -------------------------------------------------------------------------
    // Assemblage du quiz
    // -------------------------------------------------------------------------
    @Nested
    class AssembleQuiz {

        @Test
        void should_throw_when_no_technology_found() {
            when(technologyCsvRepository.findAll()).thenReturn(List.of(
                    createTechnologyCsv(1, "Java")
            ));

            final var request = new CsvQuizRequestDTO("Kotlin", "senior");

            assertThatThrownBy(() -> service.assembleFromCsv(request))
                    .isInstanceOf(QuizNotFoundException.class)
                    .hasMessageContaining("Aucune technologie trouvée");
        }

        @Test
        void should_throw_when_no_questions_found() {
            final var tech = createTechnologyCsv(1, "Java");
            when(technologyCsvRepository.findAll()).thenReturn(List.of(tech));
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

            when(technologyCsvRepository.findAll()).thenReturn(List.of(techJava, techSpring));

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

            when(questionCsvRepository.findByTechnologyIdsAndSeniority(anyList(), eq("confirme")))
                    .thenReturn(allQuestions);

            final var request = new CsvQuizRequestDTO("Java, Spring Boot", "confirme");
            final var result = service.assembleFromCsv(request);

            assertThat(result.targetSeniority()).isEqualTo("confirme");
            assertThat(result.createdBy()).isEqualTo(CsvQuizService.CREATED_BY);
            assertThat(result.questions()).hasSize(5);
            assertThat(result.questions().stream().filter(q -> q.technology().equals("Java")).count())
                    .isEqualTo(3);
            assertThat(result.questions().stream().filter(q -> q.technology().equals("Spring Boot")).count())
                    .isEqualTo(2);
        }

        @Test
        void should_fallback_to_all_seniority_levels_when_none_match() {
            final var tech = createTechnologyCsv(1, "Docker");
            when(technologyCsvRepository.findAll()).thenReturn(List.of(tech));
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
            when(technologyCsvRepository.findAll()).thenReturn(List.of(tech));
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
        void should_work_without_separators() {
            final var techJava = createTechnologyCsv(1, "Java");
            final var techDocker = createTechnologyCsv(2, "Docker");

            when(technologyCsvRepository.findAll()).thenReturn(List.of(techJava, techDocker));
            when(questionCsvRepository.findByTechnologyIdsAndSeniority(anyList(), eq("junior")))
                    .thenReturn(List.of(
                            createQuestionCsv(50, techJava, "junior", "Q Java"),
                            createQuestionCsv(51, techDocker, "junior", "Q Docker")
                    ));

            final var request = new CsvQuizRequestDTO("Java Docker", "junior");
            final var result = service.assembleFromCsv(request);

            assertThat(result.questions()).hasSize(2);
            assertThat(result.title()).contains("Java");
            assertThat(result.title()).contains("Docker");
        }
    }
}
