
package br.com.belezaecia.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class ClienteDetailsService implements UserDetailsService {

    private final JdbcTemplate jdbcTemplate;

    public ClienteDetailsService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public UserDetails loadUserByUsername(String email)
            throws UsernameNotFoundException {

        try {
            return jdbcTemplate.queryForObject(
                """
                SELECT email, senha, perfil
                FROM clientes
                WHERE LOWER(email) = LOWER(?)
                """,
                (rs, rowNum) -> {

                    String perfil = rs.getString("perfil");

                    if (!"ADMIN".equalsIgnoreCase(perfil)
                            && !"CLIENTE".equalsIgnoreCase(perfil)) {
                        throw new UsernameNotFoundException(
                            "Perfil de usuário inválido."
                        );
                    }

                    return User.withUsername(rs.getString("email"))
                        .password(rs.getString("senha"))
                        .roles(perfil.toUpperCase())
                        .build();
                },
                email.trim()
            );

        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            throw new UsernameNotFoundException(
                "E-mail não encontrado."
            );
        }
    }
}
