package com.saii.quizapi.repository;

import com.saii.quizapi.entity.TechnologyCsv;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface TechnologyCsvRepository extends JpaRepository<TechnologyCsv, Integer> {

    Optional<TechnologyCsv> findByNameIgnoreCase(String name);

    /**
     * Recherche les technologies CSV dont le nom contient le terme (case-insensitive).
     * Utilise LIKE pour le matching partiel.
     */
    @Query("SELECT t FROM TechnologyCsv t WHERE LOWER(t.name) LIKE LOWER(CONCAT('%', :term, '%'))")
    List<TechnologyCsv> findByNameContainingIgnoreCase(String term);
}
