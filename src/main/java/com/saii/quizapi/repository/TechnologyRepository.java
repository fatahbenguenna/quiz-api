package com.saii.quizapi.repository;

import com.saii.quizapi.entity.Technology;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface TechnologyRepository extends JpaRepository<Technology, Integer> {

    Optional<Technology> findByNameIgnoreCase(String name);

    /**
     * Recherche une technologie par nom exact ou dans ses aliases (text[] PostgreSQL).
     * Utilise une requête native car JPA ne gère pas nativement les tableaux PostgreSQL.
     */
    @Query(value = """
            SELECT * FROM technologies
            WHERE LOWER(name) = LOWER(:name)
               OR LOWER(:name) = ANY(SELECT LOWER(unnest(aliases)))
            LIMIT 1
            """, nativeQuery = true)
    Optional<Technology> findByNameOrAlias(String name);
}
