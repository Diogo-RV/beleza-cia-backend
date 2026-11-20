
package br.com.belezaecia.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class AgendamentoRequest {

    private Integer clienteId;

    @NotNull(message = "O serviço é obrigatório")
    @Positive(message = "O ID do serviço deve ser positivo")
    private Integer servicoId;

    @NotNull(message = "A data e hora são obrigatórias")
    private LocalDateTime dataHora;

    public Integer getClienteId() {
        return clienteId;
    }

    public void setClienteId(Integer clienteId) {
        this.clienteId = clienteId;
    }

    public Integer getServicoId() {
        return servicoId;
    }

    public void setServicoId(Integer servicoId) {
        this.servicoId = servicoId;
    }

    public LocalDateTime getDataHora() {
        return dataHora;
    }

    public void setDataHora(LocalDateTime dataHora) {
        this.dataHora = dataHora;
    }
}
