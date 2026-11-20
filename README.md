# Mary Correa Nails

Sistema web de agendamento de serviços de manicure, com frontend HTML, CSS e JavaScript, backend Java 21 + Spring Boot e banco PostgreSQL.

## Tecnologias

- Java 21 e Spring Boot
- Spring Security (autenticação, perfis e proteção CSRF)
- Spring JDBC e PostgreSQL
- HTML, CSS e JavaScript
- Maven Wrapper

## Requisitos

- Java JDK 21
- PostgreSQL

## Configuração local

1. Crie um banco PostgreSQL chamado `manicure_BD`.
2. No banco vazio, execute `database/01-estrutura-e-servicos.sql` e depois `database/02-indice-historico.sql`.
3. No PowerShell, configure as variáveis de ambiente **no mesmo terminal** que iniciará o aplicativo:

```powershell
$env:DB_URL="jdbc:postgresql://localhost:5432/manicure_BD"
$env:DB_USERNAME="postgres"
$env:DB_PASSWORD="SUA_SENHA_LOCAL"
.\mvnw.cmd spring-boot:run
```

4. Abra `http://localhost:8080/`.

O frontend é servido pelo próprio Spring Boot a partir de `src/main/resources/static/`. Não é necessário um servidor separado para executá-lo localmente.

## Testes

```powershell
.\mvnw.cmd test
```

## Configuração em hospedagem

Defina `DB_URL` (URL JDBC do PostgreSQL), `DB_USERNAME`, `DB_PASSWORD` e, se necessário, `PORT` nas variáveis de ambiente do provedor. Nunca inclua credenciais reais no repositório.

**Atenção:** antes de liberar o sistema para clientes reais, valide a infraestrutura de hospedagem, a persistência de sessões, o HTTPS, a proteção de dados pessoais, os backups e os controles de acesso.

## Banco de dados

Os scripts SQL em `database/` definem a estrutura inicial e os índices. Eles não contêm backup de clientes. Para atualizar um banco existente, avalie a migração antes de executar scripts estruturais.
