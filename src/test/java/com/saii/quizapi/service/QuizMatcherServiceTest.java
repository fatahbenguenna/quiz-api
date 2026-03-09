package com.saii.quizapi.service;

import com.saii.quizapi.dto.MatchRequestDTO;
import com.saii.quizapi.dto.TechPrerequisiteDTO;
import com.saii.quizapi.entity.QuizTemplate;
import com.saii.quizapi.repository.QuestionRepository;
import com.saii.quizapi.repository.QuizTemplateRepository;
import com.saii.quizapi.repository.SeniorityLevelRepository;
import com.saii.quizapi.repository.TechnologyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import static com.saii.quizapi.TestFixtures.TEST_NOW;
import static com.saii.quizapi.TestFixtures.createQuestion;
import static com.saii.quizapi.TestFixtures.createSeniorityLevel;
import static com.saii.quizapi.TestFixtures.createTechnology;
import static com.saii.quizapi.TestFixtures.setField;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuizMatcherServiceTest {

    @Mock
    private TechnologyRepository technologyRepository;

    @Mock
    private QuestionRepository questionRepository;

    @Mock
    private QuizTemplateRepository quizTemplateRepository;

    @Mock
    private SeniorityLevelRepository seniorityLevelRepository;

    private QuizMatcherService service;

    @BeforeEach
    void setUp() {
        final var fixedClock = Clock.fixed(TEST_NOW.toInstant(), TEST_NOW.getOffset());
        service = new QuizMatcherService(
                technologyRepository, questionRepository,
                quizTemplateRepository, seniorityLevelRepository, fixedClock);
    }

    @Test
    void should_throw_when_no_questions_found() {
        final var request = new MatchRequestDTO(
                "Dev Java Senior",
                List.of(new TechPrerequisiteDTO("Kotlin", "senior")),
                null
        );
        when(technologyRepository.findByNameOrAlias("Kotlin")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.matchOrAssemble(request))
                .isInstanceOf(QuizNotFoundException.class)
                .hasMessageContaining("Aucune question disponible");
    }

    @Test
    void should_assemble_quiz_when_questions_exist() {
        final var tech = createTechnology(1, "Java");
        final var question = createQuestion(10, tech, "confirme", "Qu'est-ce que le polymorphisme ?");
        final var seniorityLevel = createSeniorityLevel("confirme", (short) 3);

        when(technologyRepository.findByNameOrAlias("Java")).thenReturn(Optional.of(tech));
        when(questionRepository.findByTechnologyIdAndSeniorityLevel(1, "confirme"))
                .thenReturn(List.of(question));
        when(seniorityLevelRepository.findByCode("confirme")).thenReturn(Optional.of(seniorityLevel));
        when(quizTemplateRepository.save(any(QuizTemplate.class)))
                .thenAnswer(invocation -> {
                    final var saved = invocation.getArgument(0, QuizTemplate.class);
                    setField(saved, "id", 42);
                    return saved;
                });

        final var request = new MatchRequestDTO(
                "Dev Java Confirmé",
                List.of(new TechPrerequisiteDTO("Java", "confirme")),
                null
        );

        final var result = service.matchOrAssemble(request);

        assertThat(result.id()).isEqualTo(42);
        assertThat(result.title()).isEqualTo("Dev Java Confirmé");
        assertThat(result.targetSeniority()).isEqualTo("confirme");
        assertThat(result.createdBy()).isEqualTo(QuizMatcherService.CREATED_BY_MATCHER);
        assertThat(result.questions()).hasSize(1);
        assertThat(result.questions().getFirst().question()).isEqualTo("Qu'est-ce que le polymorphisme ?");
        assertThat(result.questions().getFirst().answerType()).isEqualTo("code");
    }

    @Test
    void should_fallback_to_all_questions_when_seniority_has_none() {
        final var tech = createTechnology(2, "Docker");
        final var question = createQuestion(20, tech, "junior", "Qu'est-ce qu'un Dockerfile ?");
        final var seniorityLevel = createSeniorityLevel("senior", (short) 4);

        when(technologyRepository.findByNameOrAlias("Docker")).thenReturn(Optional.of(tech));
        when(questionRepository.findByTechnologyIdAndSeniorityLevel(2, "senior"))
                .thenReturn(List.of());
        when(questionRepository.findByTechnologyId(2)).thenReturn(List.of(question));
        when(seniorityLevelRepository.findByCode("senior")).thenReturn(Optional.of(seniorityLevel));
        when(quizTemplateRepository.save(any(QuizTemplate.class)))
                .thenAnswer(invocation -> {
                    final var saved = invocation.getArgument(0, QuizTemplate.class);
                    setField(saved, "id", 99);
                    return saved;
                });

        final var request = new MatchRequestDTO(
                "DevOps Senior",
                List.of(new TechPrerequisiteDTO("Docker", "senior")),
                null
        );

        final var result = service.matchOrAssemble(request);

        assertThat(result.questions()).hasSize(1);
        assertThat(result.questions().getFirst().technology()).isEqualTo("Docker");
    }

    @Test
    void should_respect_max_questions_limit() {
        final var tech = createTechnology(3, "Spring");
        final var questions = List.of(
                createQuestion(30, tech, "confirme", "Question 1"),
                createQuestion(31, tech, "confirme", "Question 2"),
                createQuestion(32, tech, "confirme", "Question 3")
        );
        final var seniorityLevel = createSeniorityLevel("confirme", (short) 3);

        when(technologyRepository.findByNameOrAlias("Spring")).thenReturn(Optional.of(tech));
        when(questionRepository.findByTechnologyIdAndSeniorityLevel(3, "confirme"))
                .thenReturn(questions);
        when(seniorityLevelRepository.findByCode("confirme")).thenReturn(Optional.of(seniorityLevel));
        when(quizTemplateRepository.save(any(QuizTemplate.class)))
                .thenAnswer(invocation -> {
                    final var saved = invocation.getArgument(0, QuizTemplate.class);
                    setField(saved, "id", 100);
                    return saved;
                });

        final var request = new MatchRequestDTO(
                "Dev Spring",
                List.of(new TechPrerequisiteDTO("Spring", "confirme")),
                2
        );

        final var result = service.matchOrAssemble(request);

        assertThat(result.questions()).hasSize(2);
    }
}
