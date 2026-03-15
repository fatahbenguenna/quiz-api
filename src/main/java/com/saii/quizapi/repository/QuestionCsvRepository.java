package com.saii.quizapi.repository;

import com.saii.quizapi.entity.QuestionCsv;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface QuestionCsvRepository extends JpaRepository<QuestionCsv, Integer> {

    List<QuestionCsv> findByTechnologyIdAndSeniorityLevel(Integer technologyId, String seniorityLevel);

    List<QuestionCsv> findByTechnologyId(Integer technologyId);

    /**
     * Récupère les questions avec leur technologie en une seule requête (évite N+1).
     */
    @Query("""
            SELECT q FROM QuestionCsv q
            JOIN FETCH q.technology
            WHERE q.technology.id IN :techIds AND q.seniorityLevel = :seniority
            ORDER BY q.difficultyScore ASC
            """)
    List<QuestionCsv> findByTechnologyIdsAndSeniority(List<Integer> techIds, String seniority);
}
