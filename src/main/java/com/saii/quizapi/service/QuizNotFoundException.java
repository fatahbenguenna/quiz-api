package com.saii.quizapi.service;

public class QuizNotFoundException extends RuntimeException {

    public QuizNotFoundException(final String message) {
        super(message);
    }
}
