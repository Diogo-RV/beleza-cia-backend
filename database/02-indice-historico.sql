-- Índice recomendado para acelerar filtros normalizados de status e paginação do histórico.
-- Seguro para executar mais de uma vez: não altera nem apaga registros.
CREATE INDEX IF NOT EXISTS ix_agendamentos_status_norm_data_hora
    ON public.agendamentos ((LOWER(COALESCE(status, ''))), data_hora DESC, id DESC);

-- Índice parcial para carregar rapidamente os próximos agendamentos confirmados de cada cliente.
-- Não altera nem apaga registros e pode ser executado mais de uma vez.
CREATE INDEX IF NOT EXISTS ix_agendamentos_cliente_confirmado_data
    ON public.agendamentos (cliente_id, data_hora)
    WHERE LOWER(COALESCE(status, '')) = 'confirmado';
