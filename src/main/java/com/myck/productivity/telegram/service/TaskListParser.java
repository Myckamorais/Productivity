package com.myck.productivity.telegram.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Lê uma mensagem de texto e extrai os títulos das tarefas: uma tarefa por linha. Aceita a lista do jeito que
 * as pessoas costumam escrever no celular: com marcadores ("-", "*", "•"), numerada ("1.", "2)"), com caixinhas
 * ("[ ]", "[x]") ou sem nada.
 */
public final class TaskListParser {

    /** Máximo de tarefas criadas por mensagem; o que passar disso é ignorado e avisado. */
    public static final int MAX_TASKS = 20;

    /** Tamanho máximo do título (o mesmo da API). */
    public static final int MAX_TITLE_LENGTH = 150;

    // Marcadores no começo da linha, que podem vir combinados ("1. [ ] tarefa"): traços e símbolos de lista,
    // numeração com ponto/parêntese/traço/dois-pontos e caixinhas de seleção
    private static final Pattern LEADING_MARKERS = Pattern.compile(
            "^(?:[-*•–—+·▪►>]+\\s*|\\d{1,3}\\s*[.)\\-:]\\s*|\\[\\s*[xX✓✔]?\\s*]\\s*)+");

    private static final Pattern SPACES = Pattern.compile("\\s+");

    private TaskListParser() {
    }

    /**
     * Títulos encontrados, na ordem da mensagem: linhas em branco e linhas só com marcadores são puladas,
     * espaços repetidos viram um só e títulos longos são cortados em 150 caracteres.
     */
    public static Result parse(String text) {
        List<String> titles = new ArrayList<>();
        int ignored = 0;
        if (text == null) {
            return new Result(titles, 0);
        }
        for (String line : text.split("\\R")) {
            String title = SPACES.matcher(LEADING_MARKERS.matcher(line.strip()).replaceFirst("")).replaceAll(" ").strip();
            if (title.isEmpty()) {
                continue;
            }
            if (titles.size() == MAX_TASKS) {
                ignored++;
                continue;
            }
            titles.add(title.length() > MAX_TITLE_LENGTH ? title.substring(0, MAX_TITLE_LENGTH).strip() : title);
        }
        return new Result(titles, ignored);
    }

    /**
     * @param titles  títulos das tarefas a criar (no máximo {@link #MAX_TASKS})
     * @param ignored quantas linhas com tarefa ficaram de fora por passar do limite
     */
    public record Result(List<String> titles, int ignored) {
    }
}
