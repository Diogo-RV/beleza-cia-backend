
package br.com.belezaecia.controller;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import br.com.belezaecia.dto.ServicoRequest;
import br.com.belezaecia.service.AgendaTransactionService;
import jakarta.validation.Valid;

@RestController
public class ServicoController {

    private static final ZoneId FUSO_HORARIO_NEGOCIO =
            ZoneId.of("America/Sao_Paulo");

    private final JdbcTemplate jdbcTemplate;
    private final AgendaTransactionService agendaTransactionService;

    public ServicoController(
            JdbcTemplate jdbcTemplate,
            AgendaTransactionService agendaTransactionService) {
        this.jdbcTemplate = jdbcTemplate;
        this.agendaTransactionService = agendaTransactionService;
    }

    // LISTAR TODOS OS SERVIÇOS
    @GetMapping("/api/servicos")
    public List<Map<String, Object>> listarServicos() {

        String sql = "SELECT id, nome, preco, duracao_minutos " +
                     "FROM servicos ORDER BY id";

        return jdbcTemplate.queryForList(sql);
    }

    // BUSCAR SERVIÇO PELO ID
    @GetMapping("/api/servicos/{id}")
    public ResponseEntity<Map<String, Object>> buscarServico(
            @PathVariable Integer id) {

        String sql = "SELECT id, nome, preco, duracao_minutos " +
                     "FROM servicos WHERE id = ?";

        List<Map<String, Object>> resultado =
                jdbcTemplate.queryForList(sql, id);

        if (resultado.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(resultado.get(0));
    }

    // CADASTRAR SERVIÇO
    @PostMapping("/api/servicos")
    public ResponseEntity<Map<String, Object>> cadastrarServico(
            @Valid @RequestBody ServicoRequest servico) {
        return agendaTransactionService.executarComTrava(
            () -> cadastrarServicoComTrava(servico)
        );
    }

    private ResponseEntity<Map<String, Object>> cadastrarServicoComTrava(
            ServicoRequest servico) {
        String sql = "INSERT INTO servicos " +
                     "(nome, preco, duracao_minutos) " +
                     "VALUES (?, ?, ?) " +
                     "RETURNING id, nome, preco, duracao_minutos";

        Map<String, Object> novoServico = jdbcTemplate.queryForMap(
                sql,
                servico.getNome(),
                servico.getPreco(),
                servico.getDuracaoMinutos()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(novoServico);
    }

    // ATUALIZAR SERVIÇO
    @PutMapping("/api/servicos/{id}")
    public ResponseEntity<?> atualizarServico(
            @PathVariable Integer id,
            @Valid @RequestBody ServicoRequest servico) {
        return agendaTransactionService.executarComTrava(
            () -> atualizarServicoComTrava(id, servico)
        );
    }

    private ResponseEntity<?> atualizarServicoComTrava(
            Integer id,
            ServicoRequest servico) {
        List<Integer> duracoesAtuais = jdbcTemplate.query(
                "SELECT duracao_minutos FROM servicos WHERE id = ? FOR UPDATE",
                (rs, rowNum) -> rs.getInt("duracao_minutos"),
                id
        );

        if (duracoesAtuais.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Integer duracaoAtual = duracoesAtuais.get(0);
        Integer duracaoNova = servico.getDuracaoMinutos();

        // A duração do serviço também determina a ocupação de horários já
        // agendados. Só permitimos a mudança se ela não criar sobreposições.
        if (!duracaoAtual.equals(duracaoNova)
                && duracaoCriariaConflito(id, duracaoAtual, duracaoNova)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "erro",
                            "Não é possível alterar a duração deste serviço: a mudança criaria conflito com um atendimento, um bloqueio ou ultrapassaria o expediente de um agendamento existente. Ajuste a agenda primeiro."
                    ));
        }

        String sql = "UPDATE servicos SET nome = ?, preco = ?, " +
                     "duracao_minutos = ? WHERE id = ?";

        jdbcTemplate.update(
                sql,
                servico.getNome(),
                servico.getPreco(),
                duracaoNova,
                id
        );

        String consulta = "SELECT id, nome, preco, duracao_minutos " +
                          "FROM servicos WHERE id = ?";

        Map<String, Object> atualizado =
                jdbcTemplate.queryForMap(consulta, id);

        return ResponseEntity.ok(atualizado);
    }

    /**
     * Verifica se a nova duração faria um agendamento ativo deste serviço
     * sobrepor outro atendimento ou um bloqueio, ou ultrapassar o fim do
     * período de expediente em que foi marcado. Considera também que outras
     * reservas do mesmo serviço receberão a nova duração.
     */
    private boolean duracaoCriariaConflito(
            Integer servicoId, Integer duracaoAtual, Integer duracaoNova) {

        LocalDateTime agora = LocalDateTime.now(FUSO_HORARIO_NEGOCIO);

        Boolean conflito = jdbcTemplate.queryForObject(
                "SELECT (" +
                " EXISTS (" +
                "   SELECT 1 " +
                "   FROM agendamentos a " +
                "   JOIN agendamentos outro ON outro.id <> a.id " +
                "   JOIN servicos servico_outro ON servico_outro.id = outro.servico_id " +
                "   WHERE a.servico_id = ? " +
                "     AND LOWER(COALESCE(a.status, '')) <> 'cancelado' " +
                "     AND a.data_hora + (? * INTERVAL '1 minute') > ? " +
                "     AND LOWER(COALESCE(outro.status, '')) <> 'cancelado' " +
                "     AND a.data_hora < outro.data_hora + (" +
                "       CASE WHEN outro.servico_id = ? THEN ? " +
                "            ELSE servico_outro.duracao_minutos END " +
                "       * INTERVAL '1 minute') " +
                "     AND a.data_hora + (? * INTERVAL '1 minute') > outro.data_hora " +
                " ) OR EXISTS (" +
                "   SELECT 1 " +
                "   FROM agendamentos a " +
                "   JOIN bloqueios_agenda b " +
                "     ON a.data_hora < b.data_fim " +
                "    AND a.data_hora + (? * INTERVAL '1 minute') > b.data_inicio " +
                "   WHERE a.servico_id = ? " +
                "     AND LOWER(COALESCE(a.status, '')) <> 'cancelado' " +
                "     AND a.data_hora + (? * INTERVAL '1 minute') > ? " +
                " ) OR EXISTS (" +
                "   SELECT 1 " +
                "   FROM agendamentos a " +
                "   WHERE a.servico_id = ? " +
                "     AND LOWER(COALESCE(a.status, '')) <> 'cancelado' " +
                "     AND a.data_hora + (? * INTERVAL '1 minute') > ? " +
                "     AND a.data_hora + (? * INTERVAL '1 minute') > " +
                "       DATE_TRUNC('day', a.data_hora) + " +
                "       CASE WHEN CAST(a.data_hora AS TIME) < TIME '12:00' " +
                "            THEN INTERVAL '12 hours' " +
                "            ELSE INTERVAL '17 hours' END " +
                " )" +
                ")",
                Boolean.class,
                servicoId,
                duracaoAtual,
                agora,
                servicoId,
                duracaoNova,
                duracaoNova,
                duracaoNova,
                servicoId,
                duracaoAtual,
                agora,
                servicoId,
                duracaoAtual,
                agora,
                duracaoNova
        );

        return Boolean.TRUE.equals(conflito);
    }

    // EXCLUIR SERVIÇO
    @DeleteMapping("/api/servicos/{id}")
    public ResponseEntity<?> excluirServico(@PathVariable Integer id) {
        return agendaTransactionService.executarComTrava(
            () -> excluirServicoComTrava(id)
        );
    }

    private ResponseEntity<?> excluirServicoComTrava(Integer id) {
        try {

            int linhas = jdbcTemplate.update(
                    "DELETE FROM servicos WHERE id = ?", id
            );

            if (linhas == 0) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.noContent().build();

        } catch (DataIntegrityViolationException e) {

            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(Map.of(
                        "erro",
                        "Não é possível excluir este serviço porque ele está associado a um agendamento."
                    ));
        }
    }
}
