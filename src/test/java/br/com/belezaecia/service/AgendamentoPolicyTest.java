package br.com.belezaecia.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class AgendamentoPolicyTest {

    @Test
    void clientePodeCancelarUmDiaAntes() {
        LocalDate hoje = LocalDate.of(2026, 11, 15);
        LocalDate atendimento = hoje.plusDays(1);

        assertTrue(AgendamentoPolicy.clientePodeCancelar(atendimento, hoje));
    }

    @Test
    void clienteNaoPodeCancelarNoProprioDia() {
        LocalDate hoje = LocalDate.of(2026, 11, 15);

        assertFalse(AgendamentoPolicy.clientePodeCancelar(hoje, hoje));
    }

    @Test
    void clienteNaoPodeCancelarDepoisDaDataDoAtendimento() {
        LocalDate hoje = LocalDate.of(2026, 11, 15);
        LocalDate atendimento = hoje.minusDays(1);

        assertFalse(AgendamentoPolicy.clientePodeCancelar(atendimento, hoje));
    }

    @Test
    void dataNulaNaoPermiteCancelamento() {
        assertFalse(AgendamentoPolicy.clientePodeCancelar(null, LocalDate.of(2026, 11, 15)));
    }
}
