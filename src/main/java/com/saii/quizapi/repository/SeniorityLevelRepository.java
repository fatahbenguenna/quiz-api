package com.saii.quizapi.repository;

import com.saii.quizapi.entity.SeniorityLevel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SeniorityLevelRepository extends JpaRepository<SeniorityLevel, String> {

    Optional<SeniorityLevel> findByCode(String code);
}
