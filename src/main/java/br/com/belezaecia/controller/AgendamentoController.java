package br.com.belezaecia.controller;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.belezaecia.dto.AgendamentoRequest;
import br.com.belezaecia.service.AgendaTransactionService;
import br.com.belezaecia.service.AgendamentoPolicy;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/agendamentos")
public class AgendamentoController {

    private static final ZoneId FUSO_HORARIO_NEGOCIO =
        ZoneId.of("America/Sao_Paulo");

    private final JdbcTemplate jdbcTemplate;
    private final AgendaTransactionService agendaTransactionService;

    public AgendamentoController(
            JdbcTemplate jdbcTemplate,
            AgendaTransactionService agendaTransactionService) {
        this.jdbcTemplate = jdbcTemplate;
        this.agendaTransactionService = agendaTransactionService;
    }

    private static final String CONSULTA =
        "SELECT a.id, a.cliente_id, c.nome AS cliente_nome, " +
        "a.servico_id, s.nome AS servico_nome, " +
        "s.preco AS servico_preco, s.duracao_minutos, " +
        // Formata como data/hora local sem fuso para evitar deslocamento UTC
        // ao serializar java.sql.Timestamp do TIMESTAMP WITHOUT TIME ZONE.
        "TO_CHAR(a.data_hora, 'YYYY-MM-DD\"T\"HH24:MI:SS') AS data_hora, " +
        "a.status, a.criado_em " +
        "FROM agendamentos a " +
        "LEFT JOIN clientes c ON c.id = a.cliente_id " +
        "LEFT JOIN servicos s ON s.id = a.servico_id ";

    // Retorna apenas os intervalos ocupados (sem expor nomes ou dados de clientes).
    // A agenda pública usa essa rota para montar os horários disponíveis.
    @GetMapping("/ocupados")
    public List<Map<String, LocalDateTime>> listarIntervalosOcupados(
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {

        LocalDateTime inicioDia = data.atStartOfDay();
        LocalDateTime fimDia = data.plusDays(1).atStartOfDay();

        String sql =
            "SELECT a.data_hora AS inicio, " +
            "a.data_hora + (s.duracao_minutos * INTERVAL '1 minute') AS fim " +
            "FROM agendamentos a " +
            "JOIN servicos s ON s.id = a.servico_id " +
            "WHERE LOWER(COALESCE(a.status, '')) <> 'cancelado' " +
            "AND a.data_hora < ? " +
            "AND a.data_hora + (s.duracao_minutos * INTERVAL '1 minute') > ? " +
            "UNION ALL " +
            "SELECT b.data_inicio AS inicio, b.data_fim AS fim " +
            "FROM bloqueios_agenda b " +
            "WHERE b.data_inicio < ? AND b.data_fim > ? " +
            "ORDER BY inicio";

        return jdbcTemplate.query(
            sql,
            (rs, rowNum) -> {
                Map<String, LocalDateTime> intervalo = new java.util.LinkedHashMap<>();
                intervalo.put("inicio", rs.getObject("inicio", LocalDateTime.class));
                intervalo.put("fim", rs.getObject("fim", LocalDateTime.class));
                return intervalo;
            },
            fimDia, inicioDia, fimDia, inicioDia
        );
    }

    // Informa somente as datas bloqueadas por inteiro, sem expor motivos ou dados privados.
    // Usado pelo calendário do cliente para desabilitar dias sem atendimento.
    @GetMapping("/dias-bloqueados")
    public ResponseEntity<List<LocalDate>> listarDiasBloqueados(
            @RequestParam("inicio")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam("fim")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {

        if (fim.isBefore(inicio)) {
            return ResponseEntity.badRequest().body(List.of());
        }

        List<LocalDate> dias = jdbcTemplate.query(
            "SELECT DISTINCT CAST(data_inicio AS DATE) AS dia " +
            "FROM bloqueios_agenda " +
            "WHERE CAST(data_inicio AS DATE) BETWEEN ? AND ? " +
            "AND CAST(data_inicio AS TIME) <= TIME '08:00' " +
            "AND CAST(data_fim AS TIME) >= TIME '17:00' " +
            "ORDER BY dia",
            (rs, rowNum) -> rs.getDate("dia").toLocalDate(),
            inicio,
            fim
        );
        return ResponseEntity.ok(dias);
    }

    // ADMIN consulta somente o mês solicitado para montar o calendário.
    // CLIENTE continua recebendo apenas os próprios agendamentos.
    @GetMapping
    public ResponseEntity<?> listarAgendamentos(
            Authentication authentication,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(defaultValue = "false") boolean incluirEncerrados) {

        if (isAdmin(authentication)) {
            if ((inicio == null) != (fim == null)) {
                return ResponseEntity.badRequest().body(
                    Map.of("erro", "Informe as datas inicial e final do período.")
                );
            }
            if (inicio != null && fim.isBefore(inicio)) {
                return ResponseEntity.badRequest().body(
                    Map.of("erro", "A data final não pode ser anterior à inicial.")
                );
            }

            if (inicio != null) {
                return ResponseEntity.ok(jdbcTemplate.queryForList(
                    CONSULTA + "WHERE a.data_hora >= ? AND a.data_hora < ? " +
                    "ORDER BY a.data_hora, a.id",
                    inicio.atStartOfDay(), fim.plusDays(1).atStartOfDay()
                ));
            }

            // Mantém compatibilidade com clientes HTTP antigos que ainda não
            // enviam o intervalo do calendário.
            return ResponseEntity.ok(jdbcTemplate.queryForList(
                CONSULTA + "ORDER BY a.data_hora, a.id"
            ));
        }

        Integer clienteId = buscarIdCliente(authentication.getName());
        if (incluirEncerrados) {
            // A cliente pode consultar os próprios registros encerrados quando pedir,
            // mas eles não poluem a lista principal por padrão.
            return ResponseEntity.ok(jdbcTemplate.queryForList(
                CONSULTA + "WHERE a.cliente_id = ? ORDER BY a.data_hora DESC, a.id DESC",
                clienteId
            ));
        }

        // A lista principal da cliente mostra apenas reservas futuras ou do dia,
        // ainda confirmadas. Cancelados e concluídos continuam preservados no banco.
        LocalDateTime inicioHoje = LocalDate.now(FUSO_HORARIO_NEGOCIO).atStartOfDay();
        return ResponseEntity.ok(jdbcTemplate.queryForList(
            CONSULTA + "WHERE a.cliente_id = ? " +
            "AND LOWER(COALESCE(a.status, '')) = 'confirmado' " +
            "AND a.data_hora >= ? ORDER BY a.data_hora, a.id",
            clienteId,
            inicioHoje
        ));
    }

    // Histórico administrativo paginado. Não carrega todos os registros de uma vez.
    // Inclui atendimentos anteriores e cancelamentos, inclusive cancelamentos futuros.
    @GetMapping("/historico")
    public ResponseEntity<?> listarHistoricoAdmin(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "todos") String status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {

        if (!isAdmin(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("erro", "Somente a administradora pode consultar o histórico completo."));
        }
        if (page < 0 || size < 1 || size > 50) {
            return ResponseEntity.badRequest()
                .body(Map.of("erro", "Página inválida ou tamanho fora do limite de 1 a 50 registros."));
        }
        if ((inicio == null) != (fim == null) || (inicio != null && fim.isBefore(inicio))) {
            return ResponseEntity.badRequest()
                .body(Map.of("erro", "Informe um período válido com data inicial e final."));
        }

        StringBuilder where = new StringBuilder(
            " WHERE (a.data_hora < ? OR LOWER(COALESCE(a.status, '')) = 'cancelado') "
        );
        List<Object> parametros = new ArrayList<>();
        parametros.add(LocalDate.now(FUSO_HORARIO_NEGOCIO).atStartOfDay());

        String statusNormalizado = status.trim().toLowerCase();
        switch (statusNormalizado) {
            case "todos" -> { }
            case "concluidos", "confirmados" -> where.append(
                " AND LOWER(COALESCE(a.status, '')) = 'concluido' "
            );
            case "cancelados" -> where.append(
                " AND LOWER(COALESCE(a.status, '')) = 'cancelado' "
            );
            default -> {
                return ResponseEntity.badRequest()
                    .body(Map.of("erro", "Filtro de status inválido."));
            }
        }
        if (inicio != null) {
            where.append(" AND a.data_hora >= ? AND a.data_hora < ? ");
            parametros.add(inicio.atStartOfDay());
            parametros.add(fim.plusDays(1).atStartOfDay());
        }

        Long total = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM agendamentos a " + where,
            Long.class,
            parametros.toArray()
        );
        long totalRegistros = total == null ? 0L : total;
        long offset = (long) page * size;

        List<Object> parametrosPagina = new ArrayList<>(parametros);
        parametrosPagina.add(size);
        parametrosPagina.add(offset);
        List<Map<String, Object>> conteudo = jdbcTemplate.queryForList(
            CONSULTA + where + " ORDER BY a.data_hora DESC, a.id DESC LIMIT ? OFFSET ?",
            parametrosPagina.toArray()
        );

        Map<String, Object> resposta = new java.util.LinkedHashMap<>();
        resposta.put("content", conteudo);
        resposta.put("page", page);
        resposta.put("size", size);
        resposta.put("totalElements", totalRegistros);
        resposta.put("totalPages", totalRegistros == 0 ? 0 : (int) ((totalRegistros + size - 1) / size));
        return ResponseEntity.ok(resposta);
    }

    // CLIENTE só consulta um agendamento próprio; ADMIN consulta qualquer um.
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> buscarAgendamento(
            @PathVariable Integer id,
            Authentication authentication) {

        Map<String, Object> resultado = buscarDados(id, authentication);

        if (resultado == null) {
            // Retorna 404 também quando o agendamento pertence a outra pessoa,
            // evitando revelar que aquele ID existe.
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(resultado);
    }

    // CLIENTE agenda para si mesmo; ADMIN pode informar o cliente_id.
    @PostMapping
    public ResponseEntity<?> cadastrarAgendamento(
            @Valid @RequestBody AgendamentoRequest agendamento,
            Authentication authentication) {
        return agendaTransactionService.executarComTrava(
            () -> cadastrarAgendamentoComTrava(agendamento, authentication)
        );
    }

    private ResponseEntity<?> cadastrarAgendamentoComTrava(
            AgendamentoRequest agendamento,
            Authentication authentication) {
        Integer clienteId = isAdmin(authentication)
            ? agendamento.getClienteId()
            : buscarIdCliente(authentication.getName());

        Integer duracao = buscarDuracao(agendamento.getServicoId());

        if (duracao == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("erro", "Serviço não encontrado."));
        }

        if (clienteId != null && !clienteExiste(clienteId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("erro", "Cliente não encontrado."));
        }

        LocalDateTime inicio = agendamento.getDataHora();

        if (!horarioDentroDoExpediente(inicio, duracao)) {
            return ResponseEntity.badRequest()
                .body(Map.of("erro", "Escolha um horário dentro do expediente: segunda a sábado, das 08h às 12h ou das 13h às 17h."));
        }

        if (!inicio.isAfter(LocalDateTime.now())) {
            return ResponseEntity.badRequest()
                .body(Map.of("erro", "O agendamento deve ser futuro."));
        }

        LocalDateTime fim = inicio.plusMinutes(duracao);

        if (existeConflito(inicio, fim, null)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("erro", "Esse horário não está disponível."));
        }

        String sql =
            "INSERT INTO agendamentos " +
            "(cliente_id, servico_id, data_hora) " +
            "VALUES (?, ?, ?) RETURNING id";

        Integer novoId = jdbcTemplate.queryForObject(
            sql,
            Integer.class,
            clienteId,
            agendamento.getServicoId(),
            inicio
        );

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(buscarDados(novoId, authentication));
    }

    // CLIENTE só altera o próprio agendamento e não pode trocar o dono.
    // ADMIN pode alterar qualquer agendamento e definir o cliente_id.
    @PutMapping("/{id}")
    public ResponseEntity<?> atualizarAgendamento(
            @PathVariable Integer id,
            @Valid @RequestBody AgendamentoRequest agendamento,
            Authentication authentication) {
        return agendaTransactionService.executarComTrava(
            () -> atualizarAgendamentoComTrava(id, agendamento, authentication)
        );
    }

    private ResponseEntity<?> atualizarAgendamentoComTrava(
            Integer id,
            AgendamentoRequest agendamento,
            Authentication authentication) {
        Map<String, Object> atual = buscarDados(id, authentication);

        if (atual == null) {
            return ResponseEntity.notFound().build();
        }

        if ("cancelado".equalsIgnoreCase(
                String.valueOf(atual.get("status")))) {
            return ResponseEntity.badRequest()
                .body(Map.of(
                    "erro", "Agendamento cancelado não pode ser editado."
                ));
        }

        Integer clienteId = isAdmin(authentication)
            ? agendamento.getClienteId()
            : buscarIdCliente(authentication.getName());

        Integer duracao = buscarDuracao(agendamento.getServicoId());

        if (duracao == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("erro", "Serviço não encontrado."));
        }

        if (clienteId != null && !clienteExiste(clienteId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("erro", "Cliente não encontrado."));
        }

        LocalDateTime inicio = agendamento.getDataHora();

        if (!horarioDentroDoExpediente(inicio, duracao)) {
            return ResponseEntity.badRequest()
                .body(Map.of("erro", "Escolha um horário dentro do expediente: segunda a sábado, das 08h às 12h ou das 13h às 17h."));
        }

        if (!inicio.isAfter(LocalDateTime.now())) {
            return ResponseEntity.badRequest()
                .body(Map.of("erro", "O agendamento deve ser futuro."));
        }

        LocalDateTime fim = inicio.plusMinutes(duracao);

        if (existeConflito(inicio, fim, id)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("erro", "Esse horário não está disponível."));
        }

        int linhas;
        if (isAdmin(authentication)) {
            linhas = jdbcTemplate.update(
                "UPDATE agendamentos " +
                "SET cliente_id = ?, servico_id = ?, data_hora = ? " +
                "WHERE id = ?",
                clienteId,
                agendamento.getServicoId(),
                inicio,
                id
            );
        } else {
            // A condição adicional no SQL reforça a verificação de propriedade.
            linhas = jdbcTemplate.update(
                "UPDATE agendamentos " +
                "SET servico_id = ?, data_hora = ? " +
                "WHERE id = ? AND cliente_id = ?",
                agendamento.getServicoId(),
                inicio,
                id,
                clienteId
            );
        }

        if (linhas == 0) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(buscarDados(id, authentication));
    }

    // CLIENTE só cancela o próprio agendamento até o dia anterior.
    // ADMIN pode cancelar qualquer agendamento, inclusive no próprio dia.
    @PatchMapping("/{id}/cancelar")
    public ResponseEntity<?> cancelarAgendamento(
            @PathVariable Integer id,
            Authentication authentication) {
        return agendaTransactionService.executarComTrava(
            () -> cancelarAgendamentoComTrava(id, authentication)
        );
    }

    private ResponseEntity<?> cancelarAgendamentoComTrava(
            Integer id,
            Authentication authentication) {
        Map<String, Object> atual = buscarDados(id, authentication);

        if (atual == null) {
            return ResponseEntity.notFound().build();
        }

        String statusAtual = String.valueOf(atual.get("status"));
        if ("cancelado".equalsIgnoreCase(statusAtual)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("erro", "Este agendamento já está cancelado."));
        }
        if ("concluido".equalsIgnoreCase(statusAtual)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("erro", "Este atendimento já foi concluído e não pode ser cancelado."));
        }

        int linhas;
        if (isAdmin(authentication)) {
            // A administradora pode cancelar inclusive no dia do atendimento.
            linhas = jdbcTemplate.update(
                "UPDATE agendamentos SET status = 'cancelado' " +
                "WHERE id = ? AND LOWER(COALESCE(status, '')) <> 'cancelado'",
                id
            );
        } else {
            Integer clienteId = buscarIdCliente(authentication.getName());
            LocalDate hoje = LocalDate.now(FUSO_HORARIO_NEGOCIO);
            LocalDate dataAtendimento = jdbcTemplate.queryForObject(
                "SELECT CAST(data_hora AS DATE) FROM agendamentos WHERE id = ? AND cliente_id = ?",
                LocalDate.class,
                id,
                clienteId
            );

            if (!AgendamentoPolicy.clientePodeCancelar(dataAtendimento, hoje)) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                        "erro", "O cancelamento pelo cliente só é permitido até o dia anterior ao atendimento. No dia do atendimento, entre em contato com a Mary."
                    ));
            }

            // A condição de data também é aplicada no UPDATE, como proteção adicional.
            LocalDateTime inicioDoDiaSeguinte = hoje.plusDays(1).atStartOfDay();
            linhas = jdbcTemplate.update(
                "UPDATE agendamentos SET status = 'cancelado' " +
                "WHERE id = ? AND cliente_id = ? " +
                "AND LOWER(COALESCE(status, '')) <> 'cancelado' " +
                "AND data_hora >= ?",
                id,
                clienteId,
                inicioDoDiaSeguinte
            );
        }

        if (linhas == 0) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("erro", "Não foi possível cancelar. Atualize a agenda e tente novamente."));
        }

        return ResponseEntity.ok(buscarDados(id, authentication));
    }

    // Identifica o perfil por meio das autoridades geradas no login.
    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
            .anyMatch(authority ->
                "ROLE_ADMIN".equals(authority.getAuthority())
            );
    }

    // O e-mail vem da sessão autenticada, não do corpo enviado pelo navegador.
    private Integer buscarIdCliente(String email) {
        return jdbcTemplate.queryForObject(
            "SELECT id FROM clientes WHERE LOWER(email) = LOWER(?)",
            Integer.class,
            email
        );
    }

    // Validação no servidor: a interface não é a única barreira de horário.
    private boolean horarioDentroDoExpediente(
            LocalDateTime inicio, Integer duracaoMinutos) {

        if (inicio.getDayOfWeek() == DayOfWeek.SUNDAY
                || (inicio.getMinute() != 0 && inicio.getMinute() != 30)
                || inicio.getSecond() != 0
                || inicio.getNano() != 0) {
            return false;
        }

        LocalDateTime fim = inicio.plusMinutes(duracaoMinutos);
        LocalTime horaInicio = inicio.toLocalTime();
        LocalTime horaFim = fim.toLocalTime();

        boolean periodoManha = !horaInicio.isBefore(LocalTime.of(8, 0))
            && !horaInicio.isAfter(LocalTime.of(11, 30))
            && !horaFim.isAfter(LocalTime.of(12, 0))
            && fim.toLocalDate().equals(inicio.toLocalDate());

        boolean periodoTarde = !horaInicio.isBefore(LocalTime.of(13, 0))
            && !horaInicio.isAfter(LocalTime.of(16, 30))
            && !horaFim.isAfter(LocalTime.of(17, 0))
            && fim.toLocalDate().equals(inicio.toLocalDate());

        return periodoManha || periodoTarde;
    }

    // BUSCAR DURAÇÃO DO SERVIÇO
    private Integer buscarDuracao(Integer servicoId) {
        List<Integer> resultado = jdbcTemplate.query(
            "SELECT duracao_minutos FROM servicos WHERE id = ? AND ativo = TRUE",
            (rs, rowNum) -> rs.getInt("duracao_minutos"),
            servicoId
        );

        return resultado.isEmpty() ? null : resultado.get(0);
    }

    // VERIFICAR EXISTÊNCIA DO CLIENTE
    private boolean clienteExiste(Integer clienteId) {
        Long quantidade = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM clientes WHERE id = ?",
            Long.class,
            clienteId
        );

        return quantidade != null && quantidade > 0;
    }

    // VERIFICAR CONFLITOS DE HORÁRIO
    private boolean existeConflito(
            LocalDateTime inicio,
            LocalDateTime fim,
            Integer ignorarId) {

        Long bloqueios = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM bloqueios_agenda " +
            "WHERE data_inicio < ? AND data_fim > ?",
            Long.class,
            fim,
            inicio
        );

        if (bloqueios != null && bloqueios > 0) {
            return true;
        }

        String sql =
            "SELECT COUNT(*) FROM agendamentos a " +
            "JOIN servicos s ON s.id = a.servico_id " +
            "WHERE (a.status IS NULL OR a.status <> 'cancelado') " +
            "AND a.data_hora < ? " +
            "AND a.data_hora + " +
            "(s.duracao_minutos * INTERVAL '1 minute') > ? ";

        List<Object> parametros = new ArrayList<>();
        parametros.add(fim);
        parametros.add(inicio);

        if (ignorarId != null) {
            sql += "AND a.id <> ? ";
            parametros.add(ignorarId);
        }

        Long conflitos = jdbcTemplate.queryForObject(
            sql,
            Long.class,
            parametros.toArray()
        );

        return conflitos != null && conflitos > 0;
    }

    // BUSCAR DADOS: ADMIN por ID; CLIENTE somente se o registro for seu.
    private Map<String, Object> buscarDados(
            Integer id,
            Authentication authentication) {

        List<Map<String, Object>> resultado;

        if (isAdmin(authentication)) {
            resultado = jdbcTemplate.queryForList(
                CONSULTA + "WHERE a.id = ?",
                id
            );
        } else {
            Integer clienteId = buscarIdCliente(authentication.getName());
            resultado = jdbcTemplate.queryForList(
                CONSULTA + "WHERE a.id = ? AND a.cliente_id = ?",
                id,
                clienteId
            );
        }

        return resultado.isEmpty() ? null : resultado.get(0);
    }
}
