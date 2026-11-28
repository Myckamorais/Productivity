package com.myck.productivity.common;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Contador de tentativas por chave (IP, e-mail...) em janelas de tempo fixas: no máximo {@code maxAttempts}
 * por {@code window}. Quando a janela acaba, a contagem recomeça do zero.
 *
 * Fica em memória: basta para uma única instância da API (o caso do plano gratuito). Com várias instâncias,
 * cada uma contaria separado e seria preciso um armazenamento compartilhado (ex.: Redis).
 */
public class RateLimiter {

    // Teto de chaves guardadas: impede que alguém encha a memória mandando milhões de IPs/e-mails diferentes
    static final int MAX_KEYS = 50_000;

    private final int maxAttempts;
    private final Duration window;
    private final Clock clock;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    private record Window(Instant start, int attempts) {
    }

    public RateLimiter(int maxAttempts, Duration window, Clock clock) {
        if (maxAttempts < 1 || window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("Limite e janela precisam ser positivos.");
        }
        this.maxAttempts = maxAttempts;
        this.window = window;
        this.clock = clock;
    }

    /**
     * Conta uma tentativa e diz se ela pode seguir: vazio = liberada; senão, quanto tempo falta para a
     * janela acabar (vira o Retry-After da resposta 429).
     */
    public Optional<Duration> tryAcquire(String key) {
        Instant now = clock.instant();
        Optional<Duration> full = rejectIfFull(key, now);
        if (full.isPresent()) {
            return full;
        }
        Window current = windows.compute(key, (k, w) -> isActive(w, now)
                ? new Window(w.start(), w.attempts() + 1)
                : new Window(now, 1));
        return current.attempts() > maxAttempts ? Optional.of(remaining(current, now)) : Optional.empty();
    }

    /** Só consulta, sem contar: diz se a chave já esgotou as tentativas da janela atual. */
    public Optional<Duration> blockedFor(String key) {
        Instant now = clock.instant();
        Window current = windows.get(key);
        return isActive(current, now) && current.attempts() >= maxAttempts
                ? Optional.of(remaining(current, now))
                : Optional.empty();
    }

    /** Conta uma tentativa sem decidir nada (ex.: uma senha errada). */
    public void record(String key) {
        tryAcquire(key);
    }

    /** Zera a contagem da chave (ex.: login certo apaga as falhas anteriores). */
    public void reset(String key) {
        windows.remove(key);
    }

    private boolean isActive(Window w, Instant now) {
        return w != null && now.isBefore(w.start().plus(window));
    }

    private Duration remaining(Window w, Instant now) {
        Duration left = Duration.between(now, w.start().plus(window));
        // Arredonda para cima em segundos: "tente em 0 s" não faz sentido para quem recebe
        long seconds = Math.max(1, (left.toMillis() + 999) / 1000);
        return Duration.ofSeconds(seconds);
    }

    // Com o teto atingido, descarta as janelas vencidas; se ainda estiver cheio, recusa chaves novas
    // (melhor recusar algumas tentativas durante um ataque do que deixar a memória crescer sem limite)
    private Optional<Duration> rejectIfFull(String key, Instant now) {
        if (windows.size() < MAX_KEYS || windows.containsKey(key)) {
            return Optional.empty();
        }
        windows.entrySet().removeIf(entry -> !isActive(entry.getValue(), now));
        return windows.size() < MAX_KEYS ? Optional.empty() : Optional.of(window);
    }
}
