
package br.com.belezaecia.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class BloqueioAgendaRequest {

    @NotNull(message = "A data inicial é obrigatória")
    private LocalDateTime dataInicio;

    @NotNull(message = "A data final é obrigatória")
    private LocalDateTime dataFim;

    @Size(max = 255, message = "O motivo deve ter no máximo 255 caracteres")
    private String motivo;

    public LocalDateTime getDataInicio() {
        return dataInicio;
    }

    public void setDataInicio(LocalDateTime dataInicio) {
        this.dataInicio = dataInicio;
    }

    public LocalDateTime getDataFim() {
        return dataFim;
    }

    public void setDataFim(LocalDateTime dataFim) {
        this.dataFim = dataFim;
    }

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }
}