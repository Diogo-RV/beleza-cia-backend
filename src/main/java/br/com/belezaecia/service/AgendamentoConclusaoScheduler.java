package br.com.belezaecia.service;

import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Encerra automaticamente atendimentos confirmados quando o dia do atendimento
 * já passou. O registro permanece no banco para o histórico administrativo.
 */
@Component
@ConditionalOnProperty(
    prefix = "app.agendamentos.auto-concluir",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class AgendamentoConclusaoScheduler {

    private static final ZoneId FUSO_NEGOCIO = ZoneId.of("America/Sao_Paulo");

    private final JdbcTemplate jdbcTemplate;

    public AgendamentoConclusaoScheduler(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Corrige registros vencidos caso a aplicação tenha ficado desligada à meia-noite. */
    @EventListener(ApplicationReadyEvent.class)
    public void concluirRegistrosAoIniciar() {
        concluirAtendimentosComDataPassada();
    }

    /** Executa diariamente às 00:05 no horário de São Paulo. */
    @Scheduled(cron = "0 5 0 * * *", zone = "America/Sao_Paulo")
    public void concluirAtendimentosDiariamente() {
        concluirAtendimentosComDataPassada();
    }

    public int concluirAtendimentosComDataPassada() {
        LocalDate hoje = LocalDate.now(FUSO_NEGOCIO);
        return jdbcTemplate.update(
            "UPDATE agendamentos SET status = 'concluido' " +
            "WHERE LOWER(COALESCE(status, '')) = 'confirmado' " +
            "AND data_hora < ?",
            hoje.atStartOfDay()
        );
    }
}
