# 🚀 Deploy da API

> **Myck Productivity** · Fase 8 · API na **Render** (Docker, plano gratuito) + PostgreSQL no **Neon** (gratuito)

---

## 1. Visão geral

```
   git push (main)
        │
        ▼
┌──────────────┐   build do Dockerfile   ┌──────────────────────────┐   JDBC + SSL   ┌──────────────┐
│    GitHub    │ ──────────────────────► │   Render (web service)   │ ─────────────► │     Neon     │
│  repositório │                         │   API Spring Boot :PORT  │                │  PostgreSQL  │
└──────────────┘                         └────────────┬─────────────┘                └──────────────┘
                                                      ▲  HTTPS
                          ┌───────────────────────────┼───────────────────────────┐
                          │                           │                           │
                   Front React (CORS)        Telegram (webhook)            Swagger / Postman
```

| Peça | Onde | Por quê |
|---|---|---|
| API | Render, plano free, `runtime: docker` | Grátis sem cartão, builda o `Dockerfile` direto do GitHub, HTTPS pronto |
| Banco | Neon, plano free | Postgres gratuito **sem prazo de validade** (o da Render expira em 30 dias) |
| Região | Ambos em **US East (Virgínia)** | API e banco perto um do outro: cada consulta é rápida |
| Migrations | Flyway, na subida da API | O banco novo recebe V1…V8 sozinho no primeiro deploy |

---

## 2. Antes de começar

1. Rodar `./mvnw verify` e ver **BUILD SUCCESS** (testes + cobertura mínima)
2. Fazer commit e **push** para a `main` do GitHub (a Render builda o que está no GitHub)
3. Ter à mão o token do bot do Telegram (@BotFather), se for ligar o bot em produção

---

## 3. Banco no Neon

1. Criar conta em **neon.tech** (dá para entrar com o GitHub)
2. **Create project**
   - Nome: `myck-productivity`
   - Postgres: **17** (a mesma versão do `docker-compose.yml`)
   - Região: **AWS US East 1 (N. Virginia)**
3. No painel do projeto, **Connect** → desligar **Connection pooling** (usar a conexão **direta**)

   > Por quê: o Flyway usa recursos de sessão do Postgres (locks) que não funcionam bem através do pooler.
   > A própria API já tem o pool dela (Hikari, 5 conexões).

4. A tela mostra algo como `postgresql://USUARIO:SENHA@ep-xxxx.us-east-1.aws.neon.tech/neondb?sslmode=require`.
   Separe em três valores:

| Variável | Valor |
|---|---|
| `DB_URL` | `jdbc:postgresql://ep-xxxx.us-east-1.aws.neon.tech/neondb?sslmode=require` |
| `DB_USER` | o `USUARIO` |
| `DB_PASSWORD` | a `SENHA` |

> ⚠️ A URL JDBC começa com `jdbc:postgresql://` e **não leva** usuário e senha dentro dela.

---

## 4. API na Render

1. Criar conta em **render.com** com o GitHub
2. **New → Blueprint** → escolher o repositório `Productivity`
   - A Render lê o `render.yaml` da raiz e monta o serviço `myck-productivity-api`
3. Preencher as variáveis pedidas na tela:

| Variável | O que colocar |
|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | Os três valores do Neon (passo 3) |
| `CORS_ALLOWED_ORIGINS` | Por enquanto `http://localhost:5173`; quando o front for publicado: `https://endereco-do-front,http://localhost:5173` |
| `TELEGRAM_BOT_TOKEN` | Token do @BotFather (ou vazio para deixar o bot desligado) |
| `TELEGRAM_WEBHOOK_URL` | Deixar **vazio** no primeiro deploy (a URL ainda não existe) |
| `TELEGRAM_WEBHOOK_SECRET` | Um segredo aleatório (como gerar ⬇️) |

`JWT_SECRET` não aparece: o `render.yaml` manda a Render **gerar** um valor aleatório forte.

**Gerar o segredo do webhook** (só letras e números, como o Telegram exige):

```bash
# Git Bash
openssl rand -hex 32
```
```powershell
# PowerShell
$b = New-Object byte[] 32; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); ($b | % { $_.ToString('x2') }) -join ''
```

4. **Apply** → a Render builda a imagem (~5 min na primeira vez) e sobe a API
5. Nos **Logs**, conferir:
   - `The following 1 profile is active: "prod"`
   - `Successfully applied 8 migrations` (Flyway criando as tabelas no Neon)
   - `Started ProductivityApplication`
6. Abrir `https://myck-productivity-api.onrender.com/actuator/health` → `{"status":"UP"}`

> A URL real aparece no topo do serviço na Render (se o nome já existir, ela ganha um sufixo).

---

## 5. Ligar o bot do Telegram em produção

1. Na Render → **Environment** → `TELEGRAM_WEBHOOK_URL` = `https://SUA-URL.onrender.com/telegram/webhook`
2. Salvar (a Render reinicia a API)
3. No log: **`Webhook do Telegram registrado`**
4. No celular: mandar `/ajuda` ao bot → ele responde

> ⚠️ **Bot de desenvolvimento separado.** No perfil `dev` a API local usa *polling* e, ao subir, **apaga o
> webhook** (o Telegram não aceita os dois ao mesmo tempo). Se o `.env` local tiver o **mesmo token** da
> produção, rodar a API no computador desliga o bot de produção até o próximo deploy.
> Solução: criar um segundo bot no @BotFather só para desenvolvimento e usar o token dele no `.env` local
> (com `TELEGRAM_BOT_USERNAME` do bot de dev), ou deixar `TELEGRAM_BOT_TOKEN` vazio localmente.

---

## 6. Conferir com o smoke test

Teste automático que chama a API publicada de verdade: health, 401 sem token, Swagger com `https`, CORS do
front e o fluxo **cadastro → login → categoria → tarefa → concluir → dashboard** (apagando o que criou).

```powershell
.\mvnw.cmd test "-Dtest=AmbientePublicadoSmokeTest" "-Dsmoke.url=https://SUA-URL.onrender.com" "-Dsmoke.origin=http://localhost:5173"
```

Resultado esperado: `Tests run: 5, Failures: 0`.

> Cada execução deixa um usuário descartável `smoke-…@smoke.invalid` no banco (a API não tem exclusão de conta).

---

## 7. Limites do plano gratuito

| Limite | Efeito | Como conviver |
|---|---|---|
| Render **dorme após 15 min** sem chamadas | A primeira chamada depois disso leva **~1 min** | Normal no plano free; o smoke test já espera até 90 s |
| Dormindo, nada roda | O **aviso de fim do timer** no Telegram não sai enquanto a API dorme | Opcional: um serviço de ping (cron-job.org, UptimeRobot) chamando `/actuator/health` a cada 10 min mantém a API acordada; as 750 h/mês do plano cobrem um serviço ligado o mês todo |
| Render: **512 MB** de RAM | A API usa ~360 MB | A JVM está limitada a 75% da memória (`MaxRAMPercentage`) |
| Neon: **0,5 GB** de dados | Sobra para anos de uso pessoal | Acompanhar em *Usage* no painel |
| Neon **suspende** o banco parado | A primeira consulta acorda em ~1 s | Transparente para a API |

---

## 8. Problemas comuns

| Sintoma no log | Causa provável |
|---|---|
| `password authentication failed` | `DB_USER`/`DB_PASSWORD` trocados ou com espaço sobrando |
| `The connection attempt failed` / timeout | `DB_URL` sem `?sslmode=require` ou com o host do *pooler* |
| `JWT_SECRET precisa ter pelo menos 32 caracteres` | Variável vazia (o Blueprint não foi usado; gere uma manualmente) |
| `TELEGRAM_WEBHOOK_SECRET não configurado` | Bot com URL, mas sem segredo: o webhook não é registrado |
| Front recebe erro de CORS | O endereço do front não está em `CORS_ALLOWED_ORIGINS` (exato, com `https://` e sem `/` no fim) |
| Deploy falha no *health check* | A API não subiu: o motivo aparece no log antes do `Started` |

---

## 9. O que foi preparado no código

| Arquivo | Papel |
|---|---|
| `Dockerfile` | Build em dois estágios: JDK + Maven compilam; a imagem final tem só o JRE e o jar em camadas, roda sem root |
| `.dockerignore` | Garante que `.env`, `target/` e `.git/` nunca entrem no build |
| `render.yaml` | Serviço da Render como código (Blueprint) |
| `application.yml` | Porta pela variável `PORT`, CORS configurável, headers do proxy HTTPS e pool de 5 conexões no perfil `prod` |
| `AmbientePublicadoSmokeTest` | Smoke test do ambiente publicado |
