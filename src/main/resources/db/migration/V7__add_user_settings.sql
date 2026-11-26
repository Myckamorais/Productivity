-- Fase 5: configurações do usuário usadas pelo dashboard e pelos relatórios.
-- time_zone: fuso do usuário (ID do tipo "America/Sao_Paulo"); define onde começa e termina o "dia".
--   Usuários já existentes recebem o padrão America/Sao_Paulo.
-- daily_focus_goal_minutes: meta diária de foco, opcional (nula = sem meta); de 1 minuto a 24 horas.
ALTER TABLE users
    ADD COLUMN time_zone                VARCHAR(64) NOT NULL DEFAULT 'America/Sao_Paulo',
    ADD COLUMN daily_focus_goal_minutes INTEGER,
    ADD CONSTRAINT ck_users_daily_focus_goal CHECK (daily_focus_goal_minutes BETWEEN 1 AND 1440);
