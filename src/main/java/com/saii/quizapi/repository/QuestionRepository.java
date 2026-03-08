package com.saii.quizapi.repository;

import com.saii.quizapi.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuestionRepository extends JpaRepository<Question, Integer> {

    List<Question> findByTechnologyIdAndSeniorityLevel(Integer technologyId, String seniorityLevel);

    List<Question> findByTechnologyId(Integer technologyId);
}
