package br.com.belezaecia.service;

import java.time.LocalDate;

/** Regras de negócio puras relacionadas aos agendamentos. */
public final class AgendamentoPolicy {

    private AgendamentoPolicy() {
    }

    /**
     * A cliente pode cancelar somente antes do dia do atendimento.
     * A administradora não usa esta regra: ela pode cancelar em qualquer dia.
     */
    public static boolean clientePodeCancelar(LocalDate dataAtendimento, LocalDate hoje) {
        return dataAtendimento != null
            && hoje != null
            && dataAtendimento.isAfter(hoje);
    }
}
