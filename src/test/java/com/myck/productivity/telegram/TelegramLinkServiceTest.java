package com.myck.productivity.telegram;

import com.myck.productivity.telegram.client.TelegramProperties;
import com.myck.productivity.telegram.dto.LinkCodeResponse;
import com.myck.productivity.telegram.entity.TelegramLink;
import com.myck.productivity.telegram.entity.TelegramLinkCode;
import com.myck.productivity.telegram.repository.TelegramLinkCodeRepository;
import com.myck.productivity.telegram.repository.TelegramLinkRepository;
import com.myck.productivity.telegram.service.LinkCodeGenerator;
import com.myck.productivity.telegram.service.TelegramLinkService;
import com.myck.productivity.telegram.service.TelegramLinkService.LinkResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelegramLinkServiceTest {

    private static final Instant AGORA = Instant.parse("2026-11-25T18:00:00Z");
    private static final Long USER = 1L;
    private static final long CHAT = 555L;

    @Mock
    private TelegramLinkRepository linkRepository;
    @Mock
    private TelegramLinkCodeRepository codeRepository;
    @Mock
    private LinkCodeGenerator generator;

    private TelegramLinkService service;

    @BeforeEach
    void setUp() {
        var properties = new TelegramProperties("token", "tarefanoar_bot", "webhook", null, null,
                "https://api.telegram.org", Duration.ofMinutes(1));
        service = new TelegramLinkService(linkRepository, codeRepository, generator, properties,
                Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    // ---------- geração do código ----------

    @Test
    void deveGerarCodigoValidoPorDezMinutosComOLinkDoBot() {
        when(generator.next()).thenReturn("ABCD2345");

        LinkCodeResponse resposta = service.createCode(USER);

        assertThat(resposta.code()).isEqualTo("ABCD2345");
        assertThat(resposta.expiresAt()).isEqualTo(AGORA.plus(Duration.ofMinutes(10)));
        assertThat(resposta.deepLink()).isEqualTo("https://t.me/tarefanoar_bot?start=ABCD2345");
        verify(codeRepository).save(any(TelegramLinkCode.class));
    }

    @Test
    void deveDescartarOCodigoAnteriorAntesDeGravarONovo() {
        TelegramLinkCode antigo = new TelegramLinkCode("VELHO234", USER, AGORA);
        when(codeRepository.findByUserId(USER)).thenReturn(Optional.of(antigo));
        when(generator.next()).thenReturn("NOVO2345");

        service.createCode(USER);

        // A ordem importa: o usuário só pode ter um código (índice único no banco)
        InOrder ordem = inOrder(codeRepository);
        ordem.verify(codeRepository).delete(antigo);
        ordem.verify(codeRepository).flush();
        ordem.verify(codeRepository).save(any(TelegramLinkCode.class));
    }

    // ---------- uso do código pelo bot ----------

    @Test
    void deveVincularOChatAoDonoDoCodigoEApagarOCodigo() {
        TelegramLinkCode codigo = new TelegramLinkCode("ABCD2345", USER, AGORA.plusSeconds(60));
        when(codeRepository.findById("ABCD2345")).thenReturn(Optional.of(codigo));

        LinkResult resultado = service.linkChat("ABCD2345", CHAT);

        assertThat(resultado).isEqualTo(LinkResult.LINKED);
        verify(codeRepository).delete(codigo);
        ArgumentCaptor<TelegramLink> salvo = ArgumentCaptor.forClass(TelegramLink.class);
        verify(linkRepository).save(salvo.capture());
        assertThat(salvo.getValue().getUserId()).isEqualTo(USER);
        assertThat(salvo.getValue().getChatId()).isEqualTo(CHAT);
        assertThat(salvo.getValue().getLinkedAt()).isEqualTo(AGORA);
    }

    @Test
    void deveAceitarOCodigoDigitadoEmMinusculasEComEspacos() {
        when(codeRepository.findById("ABCD2345"))
                .thenReturn(Optional.of(new TelegramLinkCode("ABCD2345", USER, AGORA.plusSeconds(60))));

        assertThat(service.linkChat("  abcd2345 ", CHAT)).isEqualTo(LinkResult.LINKED);
    }

    @Test
    void deveRecusarEApagarOCodigoVencido() {
        TelegramLinkCode vencido = new TelegramLinkCode("ABCD2345", USER, AGORA.minusSeconds(1));
        when(codeRepository.findById("ABCD2345")).thenReturn(Optional.of(vencido));

        assertThat(service.linkChat("ABCD2345", CHAT)).isEqualTo(LinkResult.EXPIRED);
        verify(codeRepository).delete(vencido);
        verify(linkRepository, never()).save(any());
    }

    @Test
    void deveRecusarCodigoInexistente() {
        when(codeRepository.findById("ERRADO22")).thenReturn(Optional.empty());

        assertThat(service.linkChat("ERRADO22", CHAT)).isEqualTo(LinkResult.INVALID);
        verify(linkRepository, never()).save(any());
    }

    @Test
    void deveTirarOChatDaContaAnteriorAoVincularAOutraConta() {
        // O chat estava com o usuário 2; o código é do usuário 1
        TelegramLink anterior = new TelegramLink(2L);
        anterior.linkTo(CHAT, AGORA.minusSeconds(3600));
        when(codeRepository.findById("ABCD2345"))
                .thenReturn(Optional.of(new TelegramLinkCode("ABCD2345", USER, AGORA.plusSeconds(60))));
        when(linkRepository.findByChatId(CHAT)).thenReturn(Optional.of(anterior));

        service.linkChat("ABCD2345", CHAT);

        // DELETE antes do novo vínculo, pois chat_id é único
        InOrder ordem = inOrder(linkRepository);
        ordem.verify(linkRepository).delete(anterior);
        ordem.verify(linkRepository).flush();
        ordem.verify(linkRepository).save(any(TelegramLink.class));
    }

    @Test
    void deveAtualizarOVinculoExistenteQuandoOUsuarioTrocaDeChat() {
        TelegramLink existente = new TelegramLink(USER);
        existente.linkTo(111L, AGORA.minusSeconds(3600));
        existente.setDefaultCategoryId(10L);
        when(codeRepository.findById("ABCD2345"))
                .thenReturn(Optional.of(new TelegramLinkCode("ABCD2345", USER, AGORA.plusSeconds(60))));
        when(linkRepository.findById(USER)).thenReturn(Optional.of(existente));

        service.linkChat("ABCD2345", CHAT);

        assertThat(existente.getChatId()).isEqualTo(CHAT);
        // A categoria padrão continua a mesma
        assertThat(existente.getDefaultCategoryId()).isEqualTo(10L);
        verify(linkRepository, never()).delete(any());
    }

    // ---------- situação e desvínculo ----------

    @Test
    void deveInformarSeEstaVinculado() {
        TelegramLink link = new TelegramLink(USER);
        link.linkTo(CHAT, AGORA);
        when(linkRepository.findById(USER)).thenReturn(Optional.of(link));
        when(linkRepository.findById(2L)).thenReturn(Optional.empty());

        assertThat(service.status(USER).linked()).isTrue();
        assertThat(service.status(USER).linkedAt()).isEqualTo(AGORA);
        assertThat(service.status(2L).linked()).isFalse();
    }

    @Test
    void deveDesvincularSemErroMesmoSemVinculo() {
        when(linkRepository.findById(USER)).thenReturn(Optional.empty());

        service.unlink(USER);

        verify(linkRepository, never()).delete(any());
    }
}
