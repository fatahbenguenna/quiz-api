package com.saii.quizapi.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Convertisseur JPA pour stocker AnswerType en minuscules dans la colonne varchar.
 * Applique automatiquement a tous les attributs de type AnswerType.
 */
@Converter(autoApply = true)
public class AnswerTypeConverter implements AttributeConverter<AnswerType, String> {

    @Override
    public String convertToDatabaseColumn(final AnswerType answerType) {
        return answerType != null ? answerType.getValue() : null;
    }

    @Override
    public AnswerType convertToEntityAttribute(final String value) {
        return value != null ? AnswerType.fromValue(value) : null;
    }
}
