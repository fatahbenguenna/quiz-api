package com.saii.quizapi.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "seniority_levels")
@Getter
@NoArgsConstructor
public class SeniorityLevel {

    @Id
    @Column(length = 20)
    private String code;

    @Column(nullable = false, length = 50)
    private String label;

    @Column(nullable = false)
    private Short rank;
}
