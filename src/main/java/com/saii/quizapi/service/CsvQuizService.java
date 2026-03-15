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
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;

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
     * Assemble un quiz éphémère à partir d'un texte brut de technologies et d'un niveau de séniorité.
     * Découvre automatiquement les technologies présentes dans le texte (avec ou sans séparateurs).
     * Sélectionne 3 questions par technologie trouvée dans les tables CSV.
     *
     * @throws QuizNotFoundException si aucune question n'est trouvée
     */
    @Transactional(readOnly = true)
    public QuizResponseDTO assembleFromCsv(final CsvQuizRequestDTO request) {
        final var resolvedTechs = discoverTechnologies(request.technologies());

        if (resolvedTechs.isEmpty()) {
            log.warn("Aucune technologie CSV trouvée dans le texte : '{}'", request.technologies());
            throw new QuizNotFoundException("Aucune technologie trouvée dans la base CSV pour : " + request.technologies());
        }

        log.info("Technologies découvertes : {}", resolvedTechs.stream().map(TechnologyCsv::getName).toList());

        final var techIds = resolvedTechs.stream().map(TechnologyCsv::getId).toList();
        final var allQuestions = questionCsvRepository.findByTechnologyIdsAndSeniority(techIds, request.seniority());

        if (allQuestions.isEmpty()) {
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
     * Découvre les technologies présentes dans un texte brut.
     * <p>
     * Stratégie : charge toutes les technologies CSV connues, les trie par longueur de nom
     * décroissante (pour matcher "Spring Boot" avant "Spring"), puis cherche chaque nom
     * dans le texte (case-insensitive). Chaque match consomme la portion correspondante
     * du texte pour éviter les sous-matchs.
     * </p>
     *
     * @param rawText texte brut avec des noms de technologies (avec ou sans séparateurs)
     * @return liste ordonnée et dédupliquée des technologies trouvées
     */
    List<TechnologyCsv> discoverTechnologies(final String rawText) {
        final var allTechs = technologyCsvRepository.findAll();

        // Trier par longueur de nom décroissante pour prioriser les matchs longs
        final var sortedByLength = allTechs.stream()
                .sorted(Comparator.comparingInt((TechnologyCsv t) -> t.getName().length()).reversed())
                .toList();

        // Texte de travail mutable pour consommer les matchs
        var workingText = rawText;
        final var found = new LinkedHashSet<TechnologyCsv>();

        for (final var tech : sortedByLength) {
            final var pattern = buildPattern(tech.getName());
            final var matcher = pattern.matcher(workingText);

            if (matcher.find()) {
                found.add(tech);
                // Remplacer le match par des espaces pour éviter les sous-matchs
                workingText = matcher.replaceAll(" ".repeat(tech.getName().length()));
            }
        }

        return new ArrayList<>(found);
    }

    /**
     * Construit un pattern regex case-insensitive pour un nom de technologie.
     * Échappe les caractères spéciaux regex (ex: C++, C#, .NET).
     */
    private Pattern buildPattern(final String techName) {
        final var escaped = Pattern.quote(techName);
        return Pattern.compile(escaped, Pattern.CASE_INSENSITIVE);
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
     * Sélectionne exactement QUESTIONS_PER_TECHNOLOGY questions par technologie.
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
