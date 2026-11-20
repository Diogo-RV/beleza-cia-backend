
package br.com.belezaecia.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;

import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration configuration)
            throws Exception {

        return configuration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SecurityContextRepository securityContextRepository)
            throws Exception {

        CsrfTokenRequestAttributeHandler csrfHandler =
            new CsrfTokenRequestAttributeHandler();

        http
            .csrf(csrf -> csrf
                .csrfTokenRepository(
                    CookieCsrfTokenRepository.withHttpOnlyFalse()
                )
                .csrfTokenRequestHandler(csrfHandler)
            )

            .securityContext(context -> context
                .securityContextRepository(securityContextRepository)
            )

            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            )

            // APIs respondem com JSON 401/403 em vez de redirecionar para uma página HTML.
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, exception) -> {
                    if (request.getRequestURI().startsWith("/api/")) {
                        response.setStatus(401);
                        response.setContentType("application/json;charset=UTF-8");
                        response.getWriter().write("{\"erro\":\"Faça login para continuar.\"}");
                    } else {
                        response.sendError(401);
                    }
                })
                .accessDeniedHandler((request, response, exception) -> {
                    if (request.getRequestURI().startsWith("/api/")) {
                        response.setStatus(403);
                        response.setContentType("application/json;charset=UTF-8");
                        response.getWriter().write("{\"erro\":\"Você não tem permissão para esta operação.\"}");
                    } else {
                        response.sendError(403);
                    }
                })
            )

            .authorizeHttpRequests(auth -> auth

                // Autenticação
                .requestMatchers(
                    HttpMethod.GET, "/api/auth/csrf"
                ).permitAll()

                .requestMatchers(
                    HttpMethod.POST, "/api/auth/login"
                ).permitAll()

                // Cadastro público de clientes
                .requestMatchers(
                    HttpMethod.POST, "/api/clientes"
                ).permitAll()

                // Clientes e administradores podem visualizar serviços
                .requestMatchers(
                    HttpMethod.GET,
                    "/api/servicos",
                    "/api/servicos/**"
                ).permitAll()

                // Somente administradores podem gerenciar serviços
                .requestMatchers(
                    "/api/servicos/**"
                ).hasRole("ADMIN")

                // Proteção dos clientes
                .requestMatchers(
                    "/api/clientes/**"
                ).hasRole("ADMIN")

                // Clientes autenticados podem cuidar dos próprios horários;
                // administradores podem gerenciar todos os agendamentos.
                .requestMatchers(
                    "/api/agendamentos",
                    "/api/agendamentos/**"
                ).hasAnyRole("ADMIN", "CLIENTE")

                // Somente administradores gerenciam bloqueios
                .requestMatchers(
                    "/api/bloqueios",
                    "/api/bloqueios/**"
                ).hasRole("ADMIN")

                // Páginas e arquivos estáticos do site
                .requestMatchers(
                    "/",
                    "/*.html",
                    "/**/*.html",
                    "/*.css",
                    "/**/*.css",
                    "/*.js",
                    "/**/*.js",
                    "/*.png",
                    "/**/*.png",
                    "/favicon.ico"
                ).permitAll()

                // Qualquer outra rota exige autenticação
                .anyRequest().authenticated()
            );

        return http.build();
    }
}
