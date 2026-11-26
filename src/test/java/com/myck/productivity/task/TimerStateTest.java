package com.myck.productivity.task;

import com.myck.productivity.task.dto.TimerState;
import com.myck.productivity.task.entity.TaskStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TimerStateTest {

    @Test
    void deveSerConcluidoQuandoATarefaEstaConcluidaMesmoComSessaoAberta() {
        assertThat(TimerState.of(TaskStatus.COMPLETED, false)).isEqualTo(TimerState.COMPLETED);
        assertThat(TimerState.of(TaskStatus.COMPLETED, true)).isEqualTo(TimerState.COMPLETED);
    }

    @Test
    void deveSerRodandoQuandoHaSessaoRodando() {
        assertThat(TimerState.of(TaskStatus.IN_PROGRESS, true)).isEqualTo(TimerState.RUNNING);
    }

    @Test
    void deveSerPausadoQuandoEmAndamentoSemSessaoRodando() {
        assertThat(TimerState.of(TaskStatus.IN_PROGRESS, false)).isEqualTo(TimerState.PAUSED);
    }

    @Test
    void deveSerParadoQuandoPendente() {
        assertThat(TimerState.of(TaskStatus.PENDING, false)).isEqualTo(TimerState.IDLE);
    }
}
