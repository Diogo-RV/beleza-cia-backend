
package br.com.belezaecia.controller;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.belezaecia.dto.ClienteRequest;
import br.com.belezaecia.dto.ClienteUpdateRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/clientes")
public class ClienteController {

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public ClienteController(
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    // LISTAR CLIENTES
    @GetMapping
    public List<Map<String, Object>> listar() {
        return jdbcTemplate.queryForList(
            "SELECT id, nome, criado_em FROM clientes ORDER BY id"
        );
    }

    // BUSCAR CLIENTE POR ID
    @GetMapping("/{id}")
    public ResponseEntity<?> buscarPorId(@PathVariable int id) {
        try {
            Map<String, Object> cliente = jdbcTemplate.queryForMap(
                "SELECT id, nome, criado_em FROM clientes WHERE id = ?",
                id
            );

            return ResponseEntity.ok(cliente);

        } catch (EmptyResultDataAccessException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("erro", "Cliente não encontrado."));
        }
    }

    // CADASTRAR CLIENTE
    @PostMapping
    public ResponseEntity<?> cadastrar(
            @Valid @RequestBody ClienteRequest cliente) {

        String cpf = cliente.getCpf().replaceAll("\\D", "");
        String email = cliente.getEmail().trim().toLowerCase(Locale.ROOT);
        String senhaHash = passwordEncoder.encode(cliente.getSenha());

        try {
            Map<String, Object> novoCliente = jdbcTemplate.queryForMap(
                """
                INSERT INTO clientes (nome, email, cpf, senha)
                VALUES (?, ?, ?, ?)
                RETURNING id, nome, email, criado_em
                """,
                cliente.getNome(),
                email,
                cpf,
                senhaHash
            );

            return ResponseEntity.status(HttpStatus.CREATED)
                .body(novoCliente);

        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of(
                    "erro", "Este CPF ou e-mail já está cadastrado."
                ));
        }
    }

    // ATUALIZAR CLIENTE
    @PutMapping("/{id}")
    public ResponseEntity<?> atualizar(
            @PathVariable int id,
            @Valid @RequestBody ClienteUpdateRequest cliente) {

        String cpf = cliente.getCpf().replaceAll("\\D", "");
        String email = cliente.getEmail().trim().toLowerCase(Locale.ROOT);

        try {
            int linhas = jdbcTemplate.update(
                """
                UPDATE clientes
                SET nome = ?, email = ?, cpf = ?
                WHERE id = ?
                """,
                cliente.getNome(),
                email,
                cpf,
                id
            );

            if (linhas == 0) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("erro", "Cliente não encontrado."));
            }

            Map<String, Object> clienteAtualizado =
                jdbcTemplate.queryForMap(
                    """
                    SELECT id, nome, email, criado_em
                    FROM clientes
                    WHERE id = ?
                    """,
                    id
                );

            return ResponseEntity.ok(clienteAtualizado);

        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of(
                    "erro", "Este CPF ou e-mail já está cadastrado."
                ));
        }
    }

    // EXCLUIR CLIENTE
    @DeleteMapping("/{id}")
    public ResponseEntity<?> excluir(@PathVariable int id) {
        try {
            int linhas = jdbcTemplate.update(
                "DELETE FROM clientes WHERE id = ?",
                id
            );

            if (linhas == 0) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("erro", "Cliente não encontrado."));
            }

            return ResponseEntity.ok(
                Map.of("mensagem", "Cliente excluído com sucesso.")
            );

        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of(
                    "erro", "Não é possível excluir este cliente."
                ));
        }
    }
}
