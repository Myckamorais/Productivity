package com.myck.productivity.telegram.controller;

import com.myck.productivity.auth.security.AuthenticatedUser;
import com.myck.productivity.telegram.dto.LinkCodeResponse;
import com.myck.productivity.telegram.dto.LinkStatusResponse;
import com.myck.productivity.telegram.service.TelegramLinkService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/telegram")
public class TelegramLinkController {

    private final TelegramLinkService linkService;

    public TelegramLinkController(TelegramLinkService linkService) {
        this.linkService = linkService;
    }

    /** Gera um código de vinculação válido por 10 minutos e retorna o código e o link que abre o bot com ele (201). */
    @PostMapping("/link-code")
    @ResponseStatus(HttpStatus.CREATED)
    public LinkCodeResponse createCode(@AuthenticationPrincipal AuthenticatedUser user) {
        return linkService.createCode(user.id());
    }

    /** Retorna se o usuário logado tem o Telegram vinculado e desde quando. */
    @GetMapping("/link")
    public LinkStatusResponse status(@AuthenticationPrincipal AuthenticatedUser user) {
        return linkService.status(user.id());
    }

    /** Desvincula o Telegram do usuário logado e não retorna corpo (204), mesmo que não houvesse vínculo. */
    @DeleteMapping("/link")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlink(@AuthenticationPrincipal AuthenticatedUser user) {
        linkService.unlink(user.id());
    }
}
