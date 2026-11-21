-- MIGRACAO 04: permite remover serviços do catálogo preservando o histórico.
-- Execute uma vez no Neon (beleza_cia_db) ANTES de publicar o código.
-- Execute também no PostgreSQL local se for testar esta versão localmente.
-- Não apaga agendamentos nem altera o status de serviços existentes.
-- Pode ser executado novamente sem duplicar a coluna.

ALTER TABLE public.servicos
    ADD COLUMN IF NOT EXISTS ativo BOOLEAN NOT NULL DEFAULT TRUE;
