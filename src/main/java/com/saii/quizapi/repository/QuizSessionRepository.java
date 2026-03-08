package com.saii.quizapi.repository;

import com.saii.quizapi.entity.QuizSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface QuizSessionRepository extends JpaRepository<QuizSession, Integer> {

    Optional<QuizSession> findByToken(String token);

    /**
     * Charge la session avec le quiz template, ses questions liées et les technologies
     * en une seule requête pour éviter les problèmes N+1.
     */
    @Query("""
            SELECT s FROM QuizSession s
            JOIN FETCH s.quizTemplate qt
            LEFT JOIN FETCH qt.quizQuestions qq
            LEFT JOIN FETCH qq.question q
            LEFT JOIN FETCH q.technology
            WHERE s.token = :token
            """)
    Optional<QuizSession> findByTokenWithQuizAndQuestions(String token);
}
