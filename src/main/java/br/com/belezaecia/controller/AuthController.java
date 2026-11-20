
package br.com.belezaecia.controller;

import br.com.belezaecia.dto.LoginRequest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.jdbc.core.JdbcTemplate;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final JdbcTemplate jdbcTemplate;

    public AuthController(
            AuthenticationManager authenticationManager,
            SecurityContextRepository securityContextRepository,
            JdbcTemplate jdbcTemplate) {

        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    // FORNECER TOKEN DE PROTEÇÃO CSRF
    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken csrfToken) {
        return Map.of("token", csrfToken.getToken());
    }

    // LOGIN
    @PostMapping("/login")
    public ResponseEntity<?> login(
            @Valid @RequestBody LoginRequest dados,
            HttpServletRequest request,
            HttpServletResponse response) {

        try {

            Authentication authentication =
                authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                        dados.getEmail().trim(),
                        dados.getSenha()
                    )
                );

            // Evita manter o identificador de sessão anterior
            if (request.getSession(false) != null) {
                request.changeSessionId();
            }

            SecurityContext context =
                SecurityContextHolder.createEmptyContext();

            context.setAuthentication(authentication);

            SecurityContextHolder.setContext(context);

            // Salva a autenticação na sessão
            securityContextRepository.saveContext(
                context,
                request,
                response
            );

            Map<String, Object> usuario = jdbcTemplate.queryForMap(
                """
                SELECT id, nome, email, perfil
                FROM clientes
                WHERE LOWER(email) = LOWER(?)
                """,
                authentication.getName()
            );

            return ResponseEntity.ok(
                Map.of(
                    "mensagem", "Login realizado com sucesso.",
                    "usuario", usuario
                )
            );

        } catch (AuthenticationException e) {

            SecurityContextHolder.clearContext();

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of(
                    "erro", "E-mail ou senha inválidos."
                ));
        }
    }

    // CONSULTAR USUÁRIO AUTENTICADO
    @GetMapping("/me")
    public ResponseEntity<?> usuarioAtual(
            Authentication authentication) {

        Map<String, Object> usuario = jdbcTemplate.queryForMap(
            """
            SELECT id, nome, email, perfil
            FROM clientes
            WHERE LOWER(email) = LOWER(?)
            """,
            authentication.getName()
        );

        return ResponseEntity.ok(usuario);
    }

    // ENCERRAR SESSÃO
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request) {

        SecurityContextHolder.clearContext();

        HttpSession session = request.getSession(false);

        if (session != null) {
            session.invalidate();
        }

        return ResponseEntity.ok(
            Map.of("mensagem", "Sessão encerrada com sucesso.")
        );
    }
}
