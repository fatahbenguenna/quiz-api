package com.saii.quizapi.service;

import com.saii.quizapi.dto.MatchRequest;
import com.saii.quizapi.dto.QuizQuestionDto;
import com.saii.quizapi.dto.QuizResponse;
import com.saii.quizapi.dto.TechPrerequisite;
import com.saii.quizapi.entity.Question;
import com.saii.quizapi.entity.QuizTemplate;
import com.saii.quizapi.repository.QuestionRepository;
import com.saii.quizapi.repository.QuizTemplateRepository;
import com.saii.quizapi.repository.SeniorityLevelRepository;
import com.saii.quizapi.repository.TechnologyRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class QuizMatcherService {

    static final String CREATED_BY_MATCHER = "java-matcher";
    static final String DEFAULT_SENIORITY = "confirme";
    private static final int DEFAULT_DURATION_MINUTES = 30;

    private final TechnologyRepository technologyRepository;
    private final QuestionRepository questionRepository;
    private final QuizTemplateRepository quizTemplateRepository;
    private final SeniorityLevelRepository seniorityLevelRepository;
    private final Clock clock;

    public QuizMatcherService(final TechnologyRepository technologyRepository,
                              final QuestionRepository questionRepository,
                              final QuizTemplateRepository quizTemplateRepository,
                              final SeniorityLevelRepository seniorityLevelRepository,
                              final Clock clock) {
        this.technologyRepository = technologyRepository;
        this.questionRepository = questionRepository;
        this.quizTemplateRepository = quizTemplateRepository;
        this.seniorityLevelRepository = seniorityLevelRepository;
        this.clock = clock;
    }

    /**
     * Récupère un quiz existant par son ID et le convertit en DTO.
     */
    @Transactional(readOnly = true)
    public Optional<QuizResponse> findQuizById(final int quizId) {
        return quizTemplateRepository.findById(quizId)
                .map(this::toQuizResponse);
    }

    /**
     * Assemble un nouveau quiz à partir des prérequis métier :
     * 1. Pour chaque prérequis, résout la technologie (par nom ou alias)
     * 2. Collecte les questions correspondantes (technologie + séniorité)
     * 3. Assemble un nouveau quiz template et le persiste
     */
    @Transactional
    public QuizResponse matchOrAssemble(final MatchRequest request) {
        final var collectedQuestions = collectQuestions(request.prerequisites(), request.effectiveMaxQuestions());

        if (collectedQuestions.isEmpty()) {
            log.warn("Aucune question trouvée pour les prérequis : {}", request.prerequisites());
            throw new QuizNotFoundException("Aucune question disponible pour les prérequis demandés");
        }

        final var targetSeniority = resolveTargetSeniority(request.prerequisites());

        final var quiz = new QuizTemplate(
                request.jobTitle(),
                "Quiz assemblé automatiquement par le matcher Java",
                targetSeniority,
                DEFAULT_DURATION_MINUTES,
                CREATED_BY_MATCHER,
                OffsetDateTime.now(clock)
        );

        short position = 1;
        for (final var question : collectedQuestions) {
            quiz.addQuestion(question, position++);
        }

        final var saved = quizTemplateRepository.save(quiz);
        log.info("Quiz assemblé : id={}, titre='{}', {} questions",
                saved.getId(), saved.getTitle(), collectedQuestions.size());

        return toQuizResponse(saved);
    }

    private List<Question> collectQuestions(final List<TechPrerequisite> prerequisites,
                                            final int maxQuestions) {
        final var result = new ArrayList<Question>();

        for (final var prereq : prerequisites) {
            if (result.size() >= maxQuestions) {
                break;
            }

            final var techOpt = technologyRepository.findByNameOrAlias(prereq.technology());
            if (techOpt.isEmpty()) {
                log.debug("Technologie non trouvée : '{}'", prereq.technology());
                continue;
            }

            final var tech = techOpt.get();
            var questions = questionRepository
                    .findByTechnologyIdAndSeniorityLevel(tech.getId(), prereq.seniority());

            // Fallback : si aucune question au niveau exact, prendre toutes les questions de cette techno
            if (questions.isEmpty()) {
                log.debug("Aucune question pour {} / {} — fallback sur toutes les questions {}",
                        prereq.technology(), prereq.seniority(), prereq.technology());
                questions = questionRepository.findByTechnologyId(tech.getId());
            }

            final var remaining = maxQuestions - result.size();
            result.addAll(questions.stream().limit(remaining).toList());
        }

        return result;
    }

    /**
     * Détermine la séniorité cible du quiz : prend le niveau le plus élevé parmi les prérequis.
     */
    private String resolveTargetSeniority(final List<TechPrerequisite> prerequisites) {
        return prerequisites.stream()
                .map(TechPrerequisite::seniority)
                .map(code -> seniorityLevelRepository.findByCode(code)
                        .map(sl -> new RankedSeniority(sl.getCode(), sl.getRank()))
                        .orElse(new RankedSeniority(code, (short) 0)))
                .max((a, b) -> Short.compare(a.rank(), b.rank()))
                .map(RankedSeniority::code)
                .orElse(DEFAULT_SENIORITY);
    }

    private QuizResponse toQuizResponse(final QuizTemplate quiz) {
        final var questionDtos = quiz.getQuizQuestions().stream()
                .map(link -> {
                    final var q = link.getQuestion();
                    return new QuizQuestionDto(
                            link.getPosition(),
                            q.getTechnology().getName(),
                            q.getSeniorityLevel(),
                            q.getTargetVersion(),
                            q.getQuestion(),
                            q.getAnswer(),
                            q.getExplanation(),
                            q.getDifficultyScore(),
                            q.getAnswerType().getValue()
                    );
                })
                .toList();

        return new QuizResponse(
                quiz.getId(),
                quiz.getTitle(),
                quiz.getDescription(),
                quiz.getTargetSeniority(),
                quiz.getDurationMinutes(),
                quiz.getCreatedBy(),
                quiz.getCreatedAt(),
                questionDtos
        );
    }

    private record RankedSeniority(String code, short rank) {
    }
}
