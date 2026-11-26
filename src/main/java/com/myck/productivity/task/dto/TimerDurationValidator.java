package com.myck.productivity.task.dto;

import com.myck.productivity.task.entity.TimerDurations;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class TimerDurationValidator implements ConstraintValidator<ValidTimerDuration, Integer> {

    @Override
    public boolean isValid(Integer minutes, ConstraintValidatorContext context) {
        return TimerDurations.isValid(minutes);
    }
}
