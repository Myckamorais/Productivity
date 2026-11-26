package com.myck.productivity.telegram.service;

import com.myck.productivity.telegram.client.TelegramProperties;
import com.myck.productivity.telegram.dto.LinkCodeResponse;
import com.myck.productivity.telegram.dto.LinkStatusResponse;
import com.myck.productivity.telegram.entity.TelegramLink;
import com.myck.productivity.telegram.entity.TelegramLinkCode;
import com.myck.productivity.telegram.repository.TelegramLinkCodeRepository;
import com.myck.productivity.telegram.repository.TelegramLinkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

/**
 * Vinculação da conta ao Telegram. O usuário gera um código no app (autenticado) e o envia ao bot; assim o bot
 * descobre a qual usuário pertence o chat sem nunca receber senha nem token.
 */
@Service
public class TelegramLinkService {

    /** Validade do código: curta, pois quem tiver o código vincula o próprio chat à conta. */
    static final Duration CODE_TTL = Duration.ofMinutes(10);

    private final TelegramLinkRepository linkRepository;
    private final TelegramLinkCodeRepository codeRepository;
    private final LinkCodeGenerator codeGenerator;
    private final TelegramProperties properties;
    private final Clock clock;

    public TelegramLinkService(TelegramLinkRepository linkRepository, TelegramLinkCodeRepository codeRepository,
            LinkCodeGenerator codeGenerator, TelegramProperties properties, Clock clock) {
        this.linkRepository = linkRepository;
        this.codeRepository = codeRepository;
        this.codeGenerator = codeGenerator;
        this.properties = properties;
        this.clock = clock;
    }

    /** Gera um código novo para o usuário, válido por 10 minutos. Um código anterior ainda não usado é descartado. */
    @Transactional
    public LinkCodeResponse createCode(Long userId) {
        codeRepository.findByUserId(userId).ifPresent(old -> {
            codeRepository.delete(old);
            // O DELETE precisa ir antes do INSERT: o usuário só pode ter um código (índice único)
            codeRepository.flush();
        });
        TelegramLinkCode code = new TelegramLinkCode(codeGenerator.next(), userId, clock.instant().plus(CODE_TTL));
        codeRepository.save(code);
        String deepLink = "https://t.me/" + properties.botUsername() + "?start=" + code.getCode();
        return new LinkCodeResponse(code.getCode(), code.getExpiresAt(), deepLink);
    }

    /** Diz se o usuário tem o Telegram vinculado e desde quando. */
    @Transactional(readOnly = true)
    public LinkStatusResponse status(Long userId) {
        return linkRepository.findById(userId)
                .map(link -> new LinkStatusResponse(true, link.getLinkedAt()))
                .orElse(new LinkStatusResponse(false, null));
    }

    /** Desvincula o Telegram do usuário. Sem vínculo, não faz nada (a operação é idempotente). */
    @Transactional
    public void unlink(Long userId) {
        linkRepository.findById(userId).ifPresent(linkRepository::delete);
    }

    /**
     * Usa o código recebido pelo bot. Válido: liga o chat ao dono do código (se o chat estava ligado a outra
     * conta, passa para esta) e apaga o código, que só serve uma vez. Vencido: é apagado e recusado.
     */
    @Transactional
    public LinkResult linkChat(String rawCode, long chatId) {
        Instant now = clock.instant();
        Optional<TelegramLinkCode> found = codeRepository.findById(rawCode.strip().toUpperCase(Locale.ROOT));
        if (found.isEmpty()) {
            return LinkResult.INVALID;
        }
        TelegramLinkCode code = found.get();
        codeRepository.delete(code);
        if (code.isExpiredAt(now)) {
            return LinkResult.EXPIRED;
        }
        Long userId = code.getUserId();
        // Um chat pertence a um só usuário: se estava com outra conta, ela perde o vínculo
        linkRepository.findByChatId(chatId)
                .filter(other -> !other.getUserId().equals(userId))
                .ifPresent(other -> {
                    linkRepository.delete(other);
                    // O DELETE precisa ir antes do INSERT/UPDATE: chat_id é único
                    linkRepository.flush();
                });
        TelegramLink link = linkRepository.findById(userId).orElseGet(() -> new TelegramLink(userId));
        link.linkTo(chatId, now);
        linkRepository.save(link);
        return LinkResult.LINKED;
    }

    /** Desvincula pelo lado do bot (comando /desvincular). Devolve se havia vínculo. */
    @Transactional
    public boolean unlinkChat(long chatId) {
        Optional<TelegramLink> link = linkRepository.findByChatId(chatId);
        link.ifPresent(linkRepository::delete);
        return link.isPresent();
    }

    /** Resultado da tentativa de vincular pelo código. */
    public enum LinkResult {
        LINKED,
        INVALID,
        EXPIRED
    }
}
