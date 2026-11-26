-- Fase 4: cria a tabela de sessões do cronômetro. Cada início/retomada abre uma sessão e cada pausa a fecha.
-- ended_at nulo = sessão aberta (cronômetro em andamento).
-- planned_minutes é a duração escolhida (15, 30, 45 ou 60) e fica nula no cronômetro livre.
-- expires_at = started_at + planned_minutes, guardado para a consulta saber, sem recalcular, quando o tempo acaba.
-- Uma sessão aberta que passou de expires_at já está encerrada na prática: o tempo contado para no limite
-- (a aplicação grava o ended_at certo na próxima operação do usuário).
CREATE TABLE task_sessions (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT                   NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    task_id         BIGINT                   NOT NULL REFERENCES tasks (id) ON DELETE CASCADE,
    started_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    ended_at        TIMESTAMP WITH TIME ZONE,
    planned_minutes INTEGER,
    expires_at      TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_task_sessions_planned CHECK (planned_minutes IS NULL OR planned_minutes IN (15, 30, 45, 60)),
    -- duração escolhida e data de expiração andam juntas: ou existem as duas ou nenhuma
    CONSTRAINT ck_task_sessions_expires CHECK ((planned_minutes IS NULL) = (expires_at IS NULL)),
    CONSTRAINT ck_task_sessions_period CHECK (ended_at IS NULL OR ended_at >= started_at)
);

-- Garante no banco a regra "só um cronômetro ativo por usuário": no máximo uma sessão aberta por usuário.
-- Vale mesmo se duas requisições simultâneas passarem pela checagem da aplicação.
CREATE UNIQUE INDEX uk_task_sessions_user_active ON task_sessions (user_id) WHERE ended_at IS NULL;

CREATE INDEX idx_task_sessions_task ON task_sessions (task_id);
