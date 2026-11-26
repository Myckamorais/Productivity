package com.myck.productivity.task.entity;

import java.util.List;

/** Durações que o usuário pode escolher ao iniciar ou retomar o cronômetro. */
public final class TimerDurations {

    /** Minutos permitidos. Sem escolher nenhum, o cronômetro é livre (sem limite). */
    public static final List<Integer> ALLOWED_MINUTES = List.of(15, 30, 45, 60);

    private TimerDurations() {
    }

    /** Nulo é válido (cronômetro livre); fora isso, só os valores permitidos. */
    public static boolean isValid(Integer minutes) {
        return minutes == null || ALLOWED_MINUTES.contains(minutes);
    }
}
