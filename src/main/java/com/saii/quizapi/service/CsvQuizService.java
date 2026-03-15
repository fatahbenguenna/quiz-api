package com.saii.quizapi.service;

import com.saii.quizapi.dto.CsvQuizRequestDTO;
import com.saii.quizapi.dto.QuizQuestionDTO;
import com.saii.quizapi.dto.QuizResponseDTO;
import com.saii.quizapi.entity.QuestionCsv;
import com.saii.quizapi.entity.TechnologyCsv;
import com.saii.quizapi.repository.QuestionCsvRepository;
import com.saii.quizapi.repository.TechnologyCsvRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Service pour assembler un quiz à la volée depuis les tables CSV.
 * Ne persiste pas le quiz — construit un DTO éphémère pour la génération PDF.
 */
@Service
@Slf4j
public class CsvQuizService {

    static final int QUESTIONS_PER_TECHNOLOGY = 3;
    static final String CREATED_BY = "csv-matcher";

    private final TechnologyCsvRepository technologyCsvRepository;
    private final QuestionCsvRepository questionCsvRepository;
    private final Clock clock;

    public CsvQuizService(final TechnologyCsvRepository technologyCsvRepository,
                          final QuestionCsvRepository questionCsvRepository,
                          final Clock clock) {
        this.technologyCsvRepository = technologyCsvRepository;
        this.questionCsvRepository = questionCsvRepository;
        this.clock = clock;
    }

    /**
     * Assemble un quiz éphémère à partir d'une liste de technologies (texte brut) et d'un niveau de séniorité.
     * Sélectionne 3 questions par technologie trouvée dans les tables CSV.
     *
     * @throws QuizNotFoundException si aucune question n'est trouvée
     */
    @Transactional(readOnly = true)
    public QuizResponseDTO assembleFromCsv(final CsvQuizRequestDTO request) {
        final var techNames = parseTechnologyNames(request.technologies());
        final var resolvedTechs = resolveTechnologies(techNames);

        if (resolvedTechs.isEmpty()) {
            log.warn("Aucune technologie CSV trouvée pour : {}", techNames);
            throw new QuizNotFoundException("Aucune technologie trouvée dans la base CSV pour : " + request.technologies());
        }

        final var techIds = resolvedTechs.stream().map(TechnologyCsv::getId).toList();
        final var allQuestions = questionCsvRepository.findByTechnologyIdsAndSeniority(techIds, request.seniority());

        if (allQuestions.isEmpty()) {
            // Fallback : essayer toutes les questions sans filtre de séniorité
            final var fallbackQuestions = collectFallbackQuestions(techIds);
            if (fallbackQuestions.isEmpty()) {
                throw new QuizNotFoundException(
                        "Aucune question CSV trouvée pour les technologies demandées au niveau " + request.seniority());
            }
            return buildQuizResponse(resolvedTechs, fallbackQuestions, request.seniority());
        }

        return buildQuizResponse(resolvedTechs, allQuestions, request.seniority());
    }

    /**
     * Parse le texte brut de technologies en liste de noms nettoyés.
     */
    List<String> parseTechnologyNames(final String rawText) {
        return Arrays.stream(rawText.split("[,;]"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /**
     * Résout les technologies par nom exact (case-insensitive).
     */
    private List<TechnologyCsv> resolveTechnologies(final List<String> techNames) {
        final var result = new ArrayList<TechnologyCsv>();

        for (final var name : techNames) {
            final var techOpt = technologyCsvRepository.findByNameIgnoreCase(name);
            if (techOpt.isPresent()) {
                result.add(techOpt.get());
            } else {
                log.debug("Technologie CSV non trouvée : '{}'", name);
            }
        }

        return result;
    }

    /**
     * Fallback : récupère les questions de toutes les technos sans filtre de séniorité.
     */
    private List<QuestionCsv> collectFallbackQuestions(final List<Integer> techIds) {
        final var result = new ArrayList<QuestionCsv>();
        for (final var techId : techIds) {
            result.addAll(questionCsvRepository.findByTechnologyId(techId));
        }
        return result;
    }

    /**
     * Construit le DTO de quiz à partir des questions collectées.
     * Sélectionne exactement QUESTIONS_PER_TECHNOLOGY questions par technologie (round-robin).
     */
    private QuizResponseDTO buildQuizResponse(final List<TechnologyCsv> technologies,
                                              final List<QuestionCsv> allQuestions,
                                              final String seniority) {
        final var selectedQuestions = selectQuestions(technologies, allQuestions);
        final var techNames = technologies.stream().map(TechnologyCsv::getName).toList();
        final var title = "Quiz CSV — " + String.join(", ", techNames);
        final var description = "Quiz généré à partir des technologies CSV pour le niveau " + seniority;
        final var durationMinutes = Math.max(15, selectedQuestions.size() * 4);

        return new QuizResponseDTO(
                0,
                title,
                description,
                seniority,
                durationMinutes,
                CREATED_BY,
                OffsetDateTime.now(clock),
                selectedQuestions
        );
    }

    /**
     * Sélectionne QUESTIONS_PER_TECHNOLOGY questions par technologie, triées par difficulté.
     */
    private List<QuizQuestionDTO> selectQuestions(final List<TechnologyCsv> technologies,
                                                  final List<QuestionCsv> allQuestions) {
        final var result = new ArrayList<QuizQuestionDTO>();
        var position = 1;

        for (final var tech : technologies) {
            final var techQuestions = allQuestions.stream()
                    .filter(q -> q.getTechnology().getId().equals(tech.getId()))
                    .limit(QUESTIONS_PER_TECHNOLOGY)
                    .toList();

            for (final var q : techQuestions) {
                result.add(new QuizQuestionDTO(
                        position++,
                        tech.getName(),
                        q.getSeniorityLevel(),
                        null,
                        q.getQuestion(),
                        q.getAnswer(),
                        q.getExplanation(),
                        q.getDifficultyScore(),
                        "code"
                ));
            }
        }

        return result;
    }
}
