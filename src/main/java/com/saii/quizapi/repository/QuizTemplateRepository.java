package com.saii.quizapi.repository;

import com.saii.quizapi.entity.QuizTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface QuizTemplateRepository extends JpaRepository<QuizTemplate, Integer> {

    List<QuizTemplate> findByTargetSeniority(String targetSeniority);

    @Query("SELECT qt FROM QuizTemplate qt WHERE qt.sourceOfferId = :offerId")
    List<QuizTemplate> findBySourceOfferId(Integer offerId);
}
