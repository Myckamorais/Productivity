package com.myck.productivity.common;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.ZoneId;

public class TimeZoneValidator implements ConstraintValidator<ValidTimeZone, String> {

    // Só os identificadores de região conhecidos pelo Java (os mesmos que o PostgreSQL entende em AT TIME ZONE)
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || ZoneId.getAvailableZoneIds().contains(value);
    }
}
