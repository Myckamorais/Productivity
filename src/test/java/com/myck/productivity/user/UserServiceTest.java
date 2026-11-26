package com.myck.productivity.user;

import com.myck.productivity.common.ResourceNotFoundException;
import com.myck.productivity.user.dto.UserResponse;
import com.myck.productivity.user.dto.UserSettingsRequest;
import com.myck.productivity.user.mapper.UserMapper;
import com.myck.productivity.user.repository.UserRepository;
import com.myck.productivity.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, new UserMapper());
    }

    @Test
    void deveRetornarOsDadosPublicosDoUsuario() {
        // Given
        var usuario = UserFactory.comId(5L);
        when(userRepository.findById(5L)).thenReturn(Optional.of(usuario));

        // When
        UserResponse resposta = userService.getById(5L);

        // Then
        assertThat(resposta.id()).isEqualTo(5L);
        assertThat(resposta.name()).isEqualTo("Ana Souza");
        assertThat(resposta.email()).isEqualTo("ana@exemplo.com");
    }

    @Test
    void deveIncluirFusoEMetaDiariaNosDadosDoUsuario() {
        // Given
        var usuario = UserFactory.comId(5L);
        usuario.setDailyFocusGoalMinutes(180);
        when(userRepository.findById(5L)).thenReturn(Optional.of(usuario));

        // When
        UserResponse resposta = userService.getById(5L);

        // Then: o padrão de fuso vem da entidade
        assertThat(resposta.timeZone()).isEqualTo("America/Sao_Paulo");
        assertThat(resposta.dailyFocusGoalMinutes()).isEqualTo(180);
    }

    @Test
    void deveAtualizarFusoEMetaDiaria() {
        // Given
        var usuario = UserFactory.comId(5L);
        when(userRepository.findById(5L)).thenReturn(Optional.of(usuario));

        // When
        UserResponse resposta = userService.updateSettings(5L, new UserSettingsRequest("Europe/Lisbon", 240));

        // Then
        assertThat(usuario.getTimeZone()).isEqualTo("Europe/Lisbon");
        assertThat(resposta.dailyFocusGoalMinutes()).isEqualTo(240);
        verify(userRepository).save(usuario);
    }

    @Test
    void deveRemoverAMetaDiariaQuandoElaNaoEInformada() {
        // Given: usuário com meta de 3h
        var usuario = UserFactory.comId(5L);
        usuario.setDailyFocusGoalMinutes(180);
        when(userRepository.findById(5L)).thenReturn(Optional.of(usuario));

        // When
        UserResponse resposta = userService.updateSettings(5L, new UserSettingsRequest("America/Sao_Paulo", null));

        // Then
        assertThat(resposta.dailyFocusGoalMinutes()).isNull();
    }

    @Test
    void deveLancarNaoEncontradoAoAtualizarConfiguracoesDeUsuarioInexistente() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateSettings(99L, new UserSettingsRequest("UTC", null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deveLancarNaoEncontradoQuandoOUsuarioNaoExiste() {
        // Given: conta apagada depois de o token ter sido emitido
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> userService.getById(99L)).isInstanceOf(ResourceNotFoundException.class);
    }
}
