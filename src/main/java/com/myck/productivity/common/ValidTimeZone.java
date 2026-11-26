package com.myck.productivity.common;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Fuso horário no formato de região da IANA (ex.: "America/Sao_Paulo", "UTC"). Nulo é aceito; quem exige
 * o campo usa @NotBlank junto. Deslocamentos fixos como "-03:00" são recusados porque não acompanham
 * mudanças de horário de verão.
 */
@Documented
@Constraint(validatedBy = TimeZoneValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidTimeZone {

    String message() default "Fuso horário inválido. Use um identificador como America/Sao_Paulo.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
