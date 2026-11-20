
package br.com.belezaecia.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.RestController;

import br.com.belezaecia.dto.BloqueioAgendaRequest;
import br.com.belezaecia.service.AgendaTransactionService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/bloqueios")
public class BloqueioAgendaController {

    private final JdbcTemplate jdbcTemplate;
    private final AgendaTransactionService agendaTransactionService;

    public BloqueioAgendaController(
            JdbcTemplate jdbcTemplate,
            AgendaTransactionService agendaTransactionService) {
        this.jdbcTemplate = jdbcTemplate;
        this.agendaTransactionService = agendaTransactionService;
    }

    // ADMIN busca bloqueios somente do mês exibido no calendário.
    @GetMapping
    public ResponseEntity<?> listarBloqueios(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {

        String sql =
            "SELECT id, " +
            "TO_CHAR(data_inicio, 'YYYY-MM-DD\"T\"HH24:MI:SS') AS data_inicio, " +
            "TO_CHAR(data_fim, 'YYYY-MM-DD\"T\"HH24:MI:SS') AS data_fim, " +
            "motivo, criado_em " +
            "FROM bloqueios_agenda ";

        if ((inicio == null) != (fim == null) || (inicio != null && fim.isBefore(inicio))) {
            return ResponseEntity.badRequest()
                .body(Map.of("erro", "Informe um período válido para consultar bloqueios."));
        }
        if (inicio != null) {
            return ResponseEntity.ok(jdbcTemplate.queryForList(
                sql + "WHERE data_inicio < ? AND data_fim > ? ORDER BY data_inicio",
                fim.plusDays(1).atStartOfDay(), inicio.atStartOfDay()
            ));
        }
        // Compatibilidade com chamadas antigas sem período.
        return ResponseEntity.ok(jdbcTemplate.queryForList(
            sql + "ORDER BY data_inicio"
        ));
    }

    // BUSCAR BLOQUEIO POR ID
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> buscarBloqueio(
            @PathVariable Integer id) {

        Map<String, Object> bloqueio = buscarDados(id);

        if (bloqueio == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(bloqueio);
    }

    // CADASTRAR BLOQUEIO
    @PostMapping
    public ResponseEntity<?> cadastrarBloqueio(
            @Valid @RequestBody BloqueioAgendaRequest bloqueio) {
        return agendaTransactionService.executarComTrava(
            () -> cadastrarBloqueioComTrava(bloqueio)
        );
    }

    private ResponseEntity<?> cadastrarBloqueioComTrava(
            BloqueioAgendaRequest bloqueio) {
        LocalDateTime inicio = bloqueio.getDataInicio();
        LocalDateTime fim = bloqueio.getDataFim();

        String erroPeriodo = validarPeriodo(inicio, fim);
        if (erroPeriodo != null) {
            return ResponseEntity.badRequest()
                .body(Map.of("erro", erroPeriodo));
        }

        if (existeBloqueioConflitante(inicio, fim, null)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("erro", "Já existe outro bloqueio nesse período."));
        }

        if (existeAgendamentoConflitante(inicio, fim)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("erro", "Existem agendamentos nesse período."));
        }

        String sql =
            "INSERT INTO bloqueios_agenda " +
            "(data_inicio, data_fim, motivo) " +
            "VALUES (?, ?, ?) RETURNING id";

        Integer novoId = jdbcTemplate.queryForObject(
            sql,
            Integer.class,
            inicio,
            fim,
            normalizarMotivo(bloqueio.getMotivo())
        );

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(buscarDados(novoId));
    }

    // ATUALIZAR BLOQUEIO
    @PutMapping("/{id}")
    public ResponseEntity<?> atualizarBloqueio(
            @PathVariable Integer id,
            @Valid @RequestBody BloqueioAgendaRequest bloqueio) {
        return agendaTransactionService.executarComTrava(
            () -> atualizarBloqueioComTrava(id, bloqueio)
        );
    }

    private ResponseEntity<?> atualizarBloqueioComTrava(
            Integer id,
            BloqueioAgendaRequest bloqueio) {
        if (buscarDados(id) == null) {
            return ResponseEntity.notFound().build();
        }

        LocalDateTime inicio = bloqueio.getDataInicio();
        LocalDateTime fim = bloqueio.getDataFim();

        String erroPeriodo = validarPeriodo(inicio, fim);
        if (erroPeriodo != null) {
            return ResponseEntity.badRequest()
                .body(Map.of("erro", erroPeriodo));
        }

        if (existeBloqueioConflitante(inicio, fim, id)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("erro", "Já existe outro bloqueio nesse período."));
        }

        if (existeAgendamentoConflitante(inicio, fim)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("erro", "Existem agendamentos nesse período."));
        }

        jdbcTemplate.update(
            "UPDATE bloqueios_agenda " +
            "SET data_inicio = ?, data_fim = ?, motivo = ? " +
            "WHERE id = ?",
            inicio,
            fim,
            normalizarMotivo(bloqueio.getMotivo()),
            id
        );

        return ResponseEntity.ok(buscarDados(id));
    }

    // EXCLUIR BLOQUEIO
    @DeleteMapping("/{id}")
    public ResponseEntity<?> excluirBloqueio(
            @PathVariable Integer id) {
        return agendaTransactionService.executarComTrava(
            () -> excluirBloqueioComTrava(id)
        );
    }

    private ResponseEntity<?> excluirBloqueioComTrava(Integer id) {
        int linhas = jdbcTemplate.update(
            "DELETE FROM bloqueios_agenda WHERE id = ?", id
        );

        if (linhas == 0) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.noContent().build();
    }

    // Valida os bloqueios no servidor, mesmo que alguém altere o formulário no navegador.
    private String validarPeriodo(LocalDateTime inicio, LocalDateTime fim) {
        if (inicio == null || fim == null) {
            return "Informe a data e os horários inicial e final.";
        }

        if (!fim.isAfter(inicio)) {
            return "O horário final deve ser posterior ao inicial.";
        }

        if (!inicio.toLocalDate().equals(fim.toLocalDate())) {
            return "O bloqueio deve começar e terminar no mesmo dia.";
        }

        if (inicio.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return "Não é possível bloquear domingo, pois não há atendimento nesse dia.";
        }

        if (inicio.isBefore(LocalDateTime.now())) {
            return "Não é possível criar bloqueio para um horário que já começou.";
        }

        LocalTime horaInicio = inicio.toLocalTime();
        LocalTime horaFim = fim.toLocalTime();
        if (horaInicio.isBefore(LocalTime.of(8, 0))
                || horaFim.isAfter(LocalTime.of(17, 0))) {
            return "Os bloqueios devem ficar dentro do expediente, das 08h às 17h.";
        }

        if ((inicio.getMinute() != 0 && inicio.getMinute() != 30)
                || (fim.getMinute() != 0 && fim.getMinute() != 30)
                || inicio.getSecond() != 0 || fim.getSecond() != 0
                || inicio.getNano() != 0 || fim.getNano() != 0) {
            return "Utilize horários em intervalos de 30 minutos.";
        }

        return null;
    }

    // VERIFICAR OUTROS BLOQUEIOS NO MESMO PERÍODO
    private boolean existeBloqueioConflitante(
            LocalDateTime inicio,
            LocalDateTime fim,
            Integer ignorarId) {

        String sql =
            "SELECT COUNT(*) FROM bloqueios_agenda " +
            "WHERE data_inicio < ? AND data_fim > ?";

        Long quantidade;

        if (ignorarId != null) {
            sql += " AND id <> ?";

            quantidade = jdbcTemplate.queryForObject(
                sql, Long.class, fim, inicio, ignorarId
            );
        } else {
            quantidade = jdbcTemplate.queryForObject(
                sql, Long.class, fim, inicio
            );
        }

        return quantidade != null && quantidade > 0;
    }

    // VERIFICAR AGENDAMENTOS NO PERÍODO
    private boolean existeAgendamentoConflitante(
            LocalDateTime inicio,
            LocalDateTime fim) {

        String sql =
            "SELECT COUNT(*) FROM agendamentos a " +
            "JOIN servicos s ON s.id = a.servico_id " +
            "WHERE (a.status IS NULL OR LOWER(a.status) <> 'cancelado') " +
            "AND a.data_hora < ? " +
            "AND a.data_hora + " +
            "(s.duracao_minutos * INTERVAL '1 minute') > ?";

        Long quantidade = jdbcTemplate.queryForObject(
            sql, Long.class, fim, inicio
        );

        return quantidade != null && quantidade > 0;
    }

    // BUSCAR DADOS DO BLOQUEIO
    private Map<String, Object> buscarDados(Integer id) {

        List<Map<String, Object>> resultado =
            jdbcTemplate.queryForList(
                "SELECT id, " +
                "TO_CHAR(data_inicio, 'YYYY-MM-DD\"T\"HH24:MI:SS') AS data_inicio, " +
                "TO_CHAR(data_fim, 'YYYY-MM-DD\"T\"HH24:MI:SS') AS data_fim, " +
                "motivo, criado_em " +
                "FROM bloqueios_agenda WHERE id = ?",
                id
            );

        return resultado.isEmpty() ? null : resultado.get(0);
    }

    // TRATAR MOTIVO VAZIO
    private String normalizarMotivo(String motivo) {

        if (motivo == null || motivo.trim().isEmpty()) {
            return null;
        }

        return motivo.trim();
    }
}
