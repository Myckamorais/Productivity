-- Fase 4 (ajuste): a pausa passa a preservar o tempo restante do timer.
-- block_id identifica o "bloco" de tempo escolhido: o start (ou um resume com nova duração) cria um bloco
-- e os resumes sem duração continuam o mesmo bloco, gastando o que sobrou. O restante de um bloco é
-- planned_minutes menos a soma do tempo das sessões que têm o mesmo block_id.
-- Em cada sessão, expires_at continua sendo o momento em que ELA para; numa sessão de continuação
-- ele é started_at + o restante (e não started_at + planned_minutes).
-- Cronômetro livre não tem bloco: block_id nulo.
ALTER TABLE task_sessions ADD COLUMN block_id UUID;

-- Sessões de timer já existentes (se houver) viram um bloco próprio cada uma
UPDATE task_sessions SET block_id = gen_random_uuid() WHERE planned_minutes IS NOT NULL;

-- Duração escolhida e bloco andam juntos: ou existem os dois ou nenhum
ALTER TABLE task_sessions
    ADD CONSTRAINT ck_task_sessions_block CHECK ((planned_minutes IS NULL) = (block_id IS NULL));
