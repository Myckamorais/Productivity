package com.myck.productivity.goal;

import com.myck.productivity.goal.dto.GoalProgress;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GoalProgressTest {

    @Test
    void deveRetornarZeroQuandoAMetaNaoTemTarefas() {
        // Sem tarefas não pode haver divisão por zero
        assertThat(GoalProgress.EMPTY.percent()).isZero();
        assertThat(new GoalProgress(0, 0).percent()).isZero();
    }

    @Test
    void deveRetornarZeroQuandoNenhumaTarefaFoiConcluida() {
        assertThat(new GoalProgress(5, 0).percent()).isZero();
    }

    @Test
    void deveRetornarCemQuandoTodasAsTarefasForamConcluidas() {
        assertThat(new GoalProgress(4, 4).percent()).isEqualTo(100);
    }

    @Test
    void deveCalcularMetade() {
        assertThat(new GoalProgress(10, 5).percent()).isEqualTo(50);
    }

    @Test
    void deveArredondarParaBaixoQuandoAFracaoPassaDeUmTerco() {
        // 1/3 = 33,33...%
        assertThat(new GoalProgress(3, 1).percent()).isEqualTo(33);
    }

    @Test
    void deveArredondarParaCimaQuandoAFracaoPassaDeDoisTercos() {
        // 2/3 = 66,66...%
        assertThat(new GoalProgress(3, 2).percent()).isEqualTo(67);
    }

    @Test
    void deveManterOsTotaisInformados() {
        GoalProgress progresso = new GoalProgress(8, 3);
        assertThat(progresso.totalTasks()).isEqualTo(8);
        assertThat(progresso.completedTasks()).isEqualTo(3);
    }
}
