package br.com.belezaecia.service;

import java.util.function.Supplier;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Serializa alterações que afetam a disponibilidade da agenda.
 * A trava é mantida até o commit da transação, inclusive enquanto as consultas
 * de conflito e a gravação são executadas.
 */
@Service
public class AgendaTransactionService {

    private static final String LOCK_AGENDA_SQL =
        "SELECT pg_advisory_xact_lock(1357911, 24680)";

    private final JdbcTemplate jdbcTemplate;

    public AgendaTransactionService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public <T> T executarComTrava(Supplier<T> operacao) {
        jdbcTemplate.execute(LOCK_AGENDA_SQL);
        return operacao.get();
    }
}
