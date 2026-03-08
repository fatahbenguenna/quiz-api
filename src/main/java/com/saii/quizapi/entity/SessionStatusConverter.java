package com.saii.quizapi.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Convertisseur JPA pour stocker SessionStatus en snake_case dans la colonne varchar.
 * Appliqué automatiquement à tous les attributs de type SessionStatus.
 */
@Converter(autoApply = true)
public class SessionStatusConverter implements AttributeConverter<SessionStatus, String> {

    @Override
    public String convertToDatabaseColumn(final SessionStatus status) {
        return status != null ? status.getValue() : null;
    }

    @Override
    public SessionStatus convertToEntityAttribute(final String value) {
        return value != null ? SessionStatus.fromValue(value) : null;
    }
}
