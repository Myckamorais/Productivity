# 🤖 Integração com o Telegram

> **Myck Productivity** · bot **@tarefanoar_bot** · Fase 6
> Criar e concluir tarefas direto do Telegram, e receber aviso quando o tempo do timer acaba.

---

## 1. O que o bot faz

| Ação no Telegram | Resultado na API |
|---|---|
| Mandar uma **lista de texto** | Cria uma tarefa por linha, planejada para **hoje** |
| `/hoje` | Lista as tarefas de hoje, **numeradas** |
| `/concluir 2` · `/concluir 1 3` | Conclui as tarefas pelo número do `/hoje` |
| `/vincular CÓDIGO` | Liga o chat à conta do usuário |
| `/desvincular` | Desliga o chat da conta |
| `/ajuda` | Mostra os comandos |
| *(automático)* | ⏰ Avisa quando o tempo escolhido de um timer acaba |

---

## 2. Visão geral da arquitetura

```
┌──────────────┐     mensagem      ┌──────────────────┐
│   Usuário    │ ────────────────► │     Telegram     │
│  (celular)   │ ◄──────────────── │    (servidores)  │
└──────────────┘     resposta      └────────┬─────────┘
                                            │
                     PRODUÇÃO: webhook      │      DEV: polling
                 Telegram chama a API  ─────┼─────  API busca as mensagens
                 POST /telegram/webhook     │      getUpdates (a cada ~25 s)
                                            ▼
                              ┌───────────────────────────┐
                              │      API (Spring Boot)     │
                              │                            │
                              │  TelegramBotService        │
                              │    ├─ lê comando ou lista  │
                              │    └─ usa TaskService      │──► PostgreSQL
                              │                            │
                              │  TelegramClient ───────────┼──► sendMessage
                              │  (RestClient, sem lib)     │
                              └───────────────────────────┘
```

> 💡 O bot usa os **mesmos services da API**. Por isso valem as mesmas regras: dono do dado,
> validações, fuso do usuário e cronômetro.

---

## 3. Como as mensagens chegam

| | **Webhook** (produção) | **Polling** (desenvolvimento) |
|---|---|---|
| Quem inicia | O Telegram chama a API | A API pergunta ao Telegram |
| Precisa de URL pública? | ✅ Sim | ❌ Não |
| Ativado por | `TELEGRAM_MODE=webhook` | Perfil `dev` (padrão local) |
| Classe | `TelegramWebhookController` | `TelegramPollingRunner` |
| Registro no Telegram | `setWebhook` ao subir a app | `deleteWebhook` ao subir a app |

---

## 4. Vinculação da conta

O bot **nunca recebe senha nem token JWT**. A ligação é feita por um código temporário.

```
   APP (logado)                    API                         TELEGRAM
       │                            │                              │
  1.   │── POST /telegram/link-code ►│                              │
       │◄── code + deepLink ─────────│  código: 8 caracteres         │
       │                            │  vale 10 min · uso único     │
  2.   │── abre o deepLink (t.me/tarefanoar_bot?start=CÓDIGO) ─────►│
       │                            │◄──────── "/start CÓDIGO" ────│
  3.   │                            │  liga chat ↔ usuário          │
       │                            │  apaga o código               │
       │                            │─────── "Conta vinculada ✅" ─►│
```

| Regra | Por quê |
|---|---|
| Código de **8 caracteres** sem `0 O 1 I L` | Fácil de digitar no celular |
| Gerado com **SecureRandom** | Imprevisível |
| Vale **10 minutos** e **uma única vez** | Quem tem o código vincula a conta |
| Gerar outro **invalida** o anterior | Só um código ativo por usuário |
| **1 chat por conta** e **1 conta por chat** | Vincular de novo move o chat para a nova conta |

---

## 5. Da mensagem às tarefas

**Regra:** uma tarefa por linha, planejada para **hoje** (no fuso do usuário), na categoria **"Telegram"**.

| Mensagem enviada | Tarefas criadas |
|---|---|
| `- Estudar Spring` | Estudar Spring |
| `2) Academia` | Academia |
| `[ ] Ler artigo` | Ler artigo |
| `• Reunião às 14h - sala 2` | Reunião às 14h - sala 2 |
| *(linha em branco)* | — ignorada |

- Remove marcadores (`-` `*` `•` `+` `>`), numeração (`1.` `2)` `3 -`) e caixinhas (`[ ]` `[x]`)
- Mantém números e hífens que fazem parte do título
- Máximo de **20 tarefas** por mensagem; o bot avisa quantas ficaram de fora
- Título cortado em **150 caracteres**
- A categoria "Telegram" é criada no primeiro uso; o id fica salvo, então **renomear não quebra nada**

---

## 6. `/hoje` e `/concluir`

```
Tarefas de hoje (25/11):
1. ⬜ Estudar Spring ❗
2. ✅ Academia
3. ⬜ Ler artigo

Para concluir: /concluir NÚMERO
```

- ⬜ pendente · ✅ concluída · ❗ prioridade alta
- A **numeração não muda** depois de concluir (as concluídas continuam na lista)
- `/concluir` aceita vários números (`1 3` ou `1,3`), ignora repetidos e avisa os inválidos
- Concluir pelo bot **fecha o cronômetro** se ele estiver rodando

---

## 7. ⏰ Aviso de fim do timer

Uma tarefa agendada (`@Scheduled`) roda **a cada minuto** e envia:

> ⏰ Acabaram os 30 min de "Estudar Spring".
> O cronômetro parou sozinho. No app, retome escolhendo uma nova duração ou finalize a tarefa.

| Situação | Avisa? |
|---|---|
| Tempo acabou e ninguém pausou | ✅ |
| Sessão já fechada exatamente no limite | ✅ |
| Pausado **antes** do fim | ❌ |
| Usuário sem Telegram vinculado | ❌ |
| Venceu há **mais de 1 hora** (app estava desligada) | ❌ |
| Já avisado antes | ❌ (coluna `expiry_notified_at`) |

> Só marca como avisado **depois** de enviar. Se o Telegram falhar, tenta de novo no minuto seguinte.

---

## 8. 🔒 Segurança

| Risco | Proteção |
|---|---|
| Alguém chamar o webhook fingindo ser o Telegram | Segredo no header `X-Telegram-Bot-Api-Secret-Token`, comparado em **tempo constante**; sem ele, **401** |
| Token do bot vazar nos logs | O token vai na URL; o cliente **mascara** (`bot***`) toda mensagem de erro |
| Tarefas criadas em dobro | O webhook **sempre responde 200**; erros viram resposta no chat (senão o Telegram reenvia) |
| Membros de um grupo mexerem nas tarefas | Só aceita **conversa privada** |
| Segredos no código ou no Git | Só em **variáveis de ambiente** (`.env`, ignorado pelo Git) |
| Bot sem configuração | Sem token, o bot fica **desligado** e o resto da API funciona normalmente |

---

## 9. ⚙️ Configuração

| Variável | Obrigatória | Descrição |
|---|---|---|
| `TELEGRAM_BOT_TOKEN` | Para ligar o bot | Token gerado pelo @BotFather |
| `TELEGRAM_BOT_USERNAME` | Não | Padrão `tarefanoar_bot` (usado no link de vinculação) |
| `TELEGRAM_MODE` | Não | `webhook` (padrão) ou `polling` (padrão no perfil `dev`) |
| `TELEGRAM_WEBHOOK_URL` | Em produção | URL pública, ex.: `https://api.exemplo.com/telegram/webhook` |
| `TELEGRAM_WEBHOOK_SECRET` | Em produção | Segredo aleatório (`openssl rand -hex 32`) |

---

## 10. 🗂️ Onde está cada coisa

```
telegram/
├── client/       TelegramClient (4 chamadas HTTP) · TelegramProperties
├── controller/   TelegramWebhookController · TelegramLinkController
├── dto/          TelegramUpdate · LinkCodeResponse · LinkStatusResponse
├── entity/       TelegramLink · TelegramLinkCode
├── repository/   TelegramLinkRepository · TelegramLinkCodeRepository · TelegramQueries
└── service/      TelegramBotService · TelegramLinkService · TaskListParser · BotCommand
                  LinkCodeGenerator · TelegramPollingRunner · TelegramWebhookRegistrar
                  TimerExpiryNotifier
```

**Endpoints da API**

| Método | Rota | Acesso | Para quê |
|---|---|---|---|
| `POST` | `/telegram/webhook` | Telegram (segredo) | Receber mensagens |
| `POST` | `/telegram/link-code` | JWT | Gerar código de vinculação |
| `GET` | `/telegram/link` | JWT | Ver se está vinculado |
| `DELETE` | `/telegram/link` | JWT | Desvincular |

**Banco (migration V8)**

| Tabela / coluna | Guarda |
|---|---|
| `telegram_links` | usuário ↔ chat e a categoria padrão do bot |
| `telegram_link_codes` | códigos temporários de vinculação |
| `task_sessions.expiry_notified_at` | quando o aviso de fim do timer foi enviado |

---

## 11. 🧭 Decisões e porquês

| Decisão | Motivo |
|---|---|
| **Sem biblioteca** (cliente próprio com `RestClient`) | A biblioteca TelegramBots usa Jackson 2 e o Spring Boot 4 usa Jackson 3. Só precisamos de 4 chamadas: `sendMessage`, `getUpdates`, `setWebhook`, `deleteWebhook` |
| **Webhook em produção, polling no dev** | O computador local não tem URL pública. O webhook ainda acorda hospedagens gratuitas que "dormem" |
| **`/concluir` pelo número** | Rápido de digitar no celular e sem ambiguidade de títulos parecidos |
| **"Hoje" no fuso do usuário** | Uma lista mandada às 22h em São Paulo é do mesmo dia, e não do dia seguinte (UTC) |

---

## 12. ✅ Testes

| Tipo | Quantidade | O que cobre |
|---|---:|---|
| Unitários | 82 | Leitura da lista (28 casos), comandos, vinculação, polling, webhook, aviso do timer, máscara do token |
| Integração | 22 | Mensagens **simuladas chegando pelo webhook**, com PostgreSQL real e o cliente do Telegram substituído por um mock |

> Critério de pronto da fase: **mandar uma lista pelo bot cria as tarefas na API**. Comprovado por teste e no uso real com o @tarefanoar_bot.

---

## 13. ▶️ Testar localmente

1. No `.env`: `TELEGRAM_BOT_TOKEN=seu-token` (sem `< >` e sem aspas)
   - Com a API publicada, use aqui o token de um **bot só de desenvolvimento**: o polling apaga o webhook ao
     subir e desligaria o bot de produção (ver `docs/deploy.md`, seção 5)
2. Subir o banco: `docker compose up -d --wait`
3. Subir a API (perfil `dev`, modo polling): `.\mvnw.cmd spring-boot:run`
4. No log, conferir: **`Bot do Telegram em modo polling`**
5. Fazer login e chamar `POST /telegram/link-code`
6. Abrir o `deepLink` no celular e tocar em **Iniciar**
7. Mandar uma lista, depois `/hoje` e `/concluir 1`
