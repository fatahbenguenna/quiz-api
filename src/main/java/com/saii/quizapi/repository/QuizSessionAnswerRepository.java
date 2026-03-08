package com.saii.quizapi.repository;

import com.saii.quizapi.entity.QuizSessionAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QuizSessionAnswerRepository extends JpaRepository<QuizSessionAnswer, Integer> {

    List<QuizSessionAnswer> findBySessionId(Integer sessionId);

    Optional<QuizSessionAnswer> findBySessionIdAndQuestionId(Integer sessionId, Integer questionId);
}
