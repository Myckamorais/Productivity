package com.myck.productivity.task.dto;

/**
 * Corpo, opcional, do start e do resume do cronômetro.
 *
 * @param durationMinutes 15, 30, 45 ou 60 para um timer com limite; omitido (ou corpo ausente) para cronômetro livre
 */
public record StartTimerRequest(@ValidTimerDuration Integer durationMinutes) {
}
