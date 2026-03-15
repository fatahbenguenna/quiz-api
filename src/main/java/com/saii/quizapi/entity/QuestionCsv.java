package com.saii.quizapi.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Entity
@Table(name = "questions_csv")
@Getter
@NoArgsConstructor
public class QuestionCsv {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "technology_csv_id", nullable = false)
    private TechnologyCsv technology;

    @Column(name = "seniority_level", nullable = false, length = 20)
    private String seniorityLevel;

    @Column(nullable = false, columnDefinition = "text")
    private String question;

    @Column(nullable = false, columnDefinition = "text")
    private String answer;

    @Column(columnDefinition = "text")
    private String explanation;

    @Column(name = "difficulty_score", nullable = false)
    private Short difficultyScore;

    @Column(name = "generated_by", nullable = false, length = 20)
    private String generatedBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
