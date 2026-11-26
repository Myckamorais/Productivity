package com.myck.productivity.task.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** A duração do cronômetro, quando informada, deve ser 15, 30, 45 ou 60 minutos. Nulo é aceito (cronômetro livre). */
@Documented
@Constraint(validatedBy = TimerDurationValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidTimerDuration {

    String message() default "A duração deve ser 15, 30, 45 ou 60 minutos.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
