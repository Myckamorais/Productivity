-- Fase 6: bot do Telegram.
-- telegram_links: vínculo entre um usuário e uma conversa (chat) do Telegram; no máximo um chat por usuário
--   e um usuário por chat. default_category_id é a categoria onde o bot cria as tarefas recebidas por texto;
--   se a categoria for apagada, o vínculo continua e o bot cria/escolhe outra (SET NULL).
-- telegram_link_codes: código temporário que o usuário gera no app e manda ao bot para vincular a conta;
--   um código ativo por usuário (gerar outro substitui o anterior).
-- task_sessions.expiry_notified_at: quando o aviso "o tempo do timer acabou" foi enviado; evita avisar duas vezes.
CREATE TABLE telegram_links (
    user_id             BIGINT PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    chat_id             BIGINT                   NOT NULL,
    default_category_id BIGINT                   REFERENCES categories (id) ON DELETE SET NULL,
    linked_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_telegram_links_chat UNIQUE (chat_id)
);

CREATE TABLE telegram_link_codes (
    code       VARCHAR(16) PRIMARY KEY,
    user_id    BIGINT                   NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_telegram_link_codes_user UNIQUE (user_id)
);

ALTER TABLE task_sessions ADD COLUMN expiry_notified_at TIMESTAMP WITH TIME ZONE;
