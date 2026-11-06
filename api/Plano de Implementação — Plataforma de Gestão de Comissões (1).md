# Plano de Implementação — Plataforma de Gestão de Comissões

Nov 4, 2026 · @MK matheus

A implementação segue **fatias verticais** (cada fatia entrega back-end e front-end funcionando) sobre um monólito modular em Spring Boot e um PWA em Next.js, com o contrato OpenAPI como ponto de encontro entre os dois. Os requisitos e regras citados (RF, RN, D) vêm do documento de requisitos do projeto.

### Progresso de implementação

- **B0 — Fundação: concluída em 06/11/2026.** Ambiente Docker, perfis, Flyway,
  segurança stateless, `ProblemDetail`, OpenAPI, CI, JaCoCo e testes de contrato
  foram implementados e validados com `mvnw test` e `mvnw verify`.
- **Próxima fase:** B1 — Modelo de dados.

## 1. Princípios e organização

- **Fatias verticais:** cada entrega fecha um fluxo de ponta a ponta, utilizável por quem testa.
- **Contrato primeiro:** o back-end publica o OpenAPI; o front gera os tipos a partir dele e usa mocks até o endpoint existir.
- **Regras de negócio isoladas:** cálculo de comissão, descontos e ciclos ficam em classes puras, sem framework, cobertas por testes em tabela.
- **Segurança desde a primeira fatia:** isolamento por usuário e testes que provam o isolamento, não só o código que o implementa.
- **Configuração por ambiente:** segredos apenas em variáveis de ambiente; perfis dev, test e prod.
- **Repositório:** monorepo com `backend/`, `frontend/`, `docs/` e `docker-compose.yml`, facilitando CI, contrato e vetores de teste compartilhados.
- **Git:** `main` protegida, branches `feat/` e `fix/`, Conventional Commits, PRs pequenos.

## 2. Back-end

### 2.1 Decisões técnicas

| Tema | Decisão |
| --- | --- |
| Base | Java 21, Spring Boot 4, Maven |
| Arquitetura | Monólito modular, pacote por feature (controller → service → repository) com regras de domínio puras |
| Banco | PostgreSQL + Flyway, `ddl-auto=validate` |
| Tipos | UUID como chave; `NUMERIC(12,2)` e `BigDecimal` para dinheiro |
| Autenticação | Spring Security stateless; JWT de acesso (cerca de 15 min); refresh token opaco, guardado como hash, com rotação |
| Google | Front envia o ID token; o back valida assinatura, `aud`, `iss`, validade e `email_verified` e emite o próprio JWT |
| Erros | RFC 7807 (`ProblemDetail`) com `code` estável, ex.: `CICLO_FECHADO`, `CLIENTE_DUPLICADO`, `DESCONTO_EXCEDE_BASE` |
| Contrato | springdoc-openapi, prefixo `/api/v1` |
| Relatórios | SQL parametrizado (`NamedParameterJdbcTemplate`) montado só a partir de enums em lista fechada (métrica, agrupamento, filtro) |
| Exportação | Strategy sobre o mesmo resultado: CSV em streaming e PDF (OpenPDF) |
| Limite de tentativas | Bucket4j nos endpoints `/auth/*` |
| E-mail | Interface `EmailSender`; Mailpit no dev, provedor a definir na produção |
| Observabilidade | Actuator, logs estruturados, correlation id; nunca logar senha, código ou token |
| Testes | JUnit 5, Mockito, AssertJ, Testcontainers (PostgreSQL), JaCoCo |
| Qualidade | Spotless (formatação) e Checkstyle |

### 2.2 Estrutura de pacotes

```plain
com.mkmateus.<projeto>
├── common      config, segurança, erros, Clock, normalização de nomes
├── auth        cadastro, verificação, login, Google, refresh, recuperação
├── usuario
├── ciclo       configuração, calculadora de ciclo, fechamento
├── servico
├── cliente
├── desconto    tipos e descontos
├── lancamento  cálculo da comissão
├── dashboard
└── relatorio   consultas e exportação
```

Dentro de cada feature: controller, DTOs (records), service, repository e entity. Entidades nunca saem na API.

### 2.3 Padrões de engenharia

- **Domínio puro:** `CalculadoraComissao`, `CalculadoraDesconto` e `CicloCalculator` não dependem de Spring e são testados com tabelas de casos (valores de borda, arredondamento `HALF_UP`, líquido nunca negativo).
- **Tempo:** `Clock` injetado. O “hoje” e a validação de data futura usam um fuso configurável (padrão `America/Recife`).
- **Ciclos rolantes:** o ciclo *n* é calculado a partir da data âncora (`âncora.plusMonths(n)`), nunca encadeando o ciclo anterior, para evitar deriva de dia (ex.: 31/jan + 1 mês).
- **Isolamento:** o usuário vem do `SecurityContext`; repositórios sempre filtram por `usuario_id` (`findByIdAndUsuarioId`). Recurso de outro usuário responde 404, para não revelar existência.
- **Concorrência:** fechar e reabrir ciclo usam lock pessimista na linha do ciclo; criar, editar e excluir lançamento adquirem lock compartilhado e checam o status. O ciclo é criado sob demanda com `UNIQUE (usuario_id, data_inicio)` e inserção idempotente.
- **Banco como segunda defesa:** `CHECK` de percentual entre 0 e 100 e valor bruto maior que zero; `CHECK` de que o desconto aponta para exatamente um alvo (lançamento ou ciclo); `UNIQUE (usuario_id, nome_normalizado)` em clientes; unicidade de e-mail e nome de usuário sem diferenciar maiúsculas.
- **Normalização de nome:** NFD, remoção de marcas diacríticas, minúsculas, `trim` e colapso de espaços; gravada em `nome_normalizado`.
- **Credenciais:** senha com BCrypt; códigos de verificação guardados como hash, comparados em tempo constante, com validade curta e número limitado de tentativas.
- **API:** Bean Validation nas entradas, paginação com tamanho máximo, `GlobalExceptionHandler` único.

### 2.4 Visão resumida das fases

| Fase | Entregas | Critério de aceite |
| --- | --- | --- |
| **B0 Fundação** | Projeto Spring, Docker Compose (Postgres + Mailpit), Flyway, perfis, `ProblemDetail`, springdoc, CI (build + testes) | `docker compose up` sobe o ambiente; CI verde; `/actuator/health` responde. |
| **B1 Modelo de dados** | Migrations de todas as entidades, com constraints e índices `(usuario_id, data)`, `(usuario_id, cliente_id)`, `(usuario_id, ciclo_id)` | Migrations aplicam do zero no Testcontainers; constraints testadas. |
| **B2 Autenticação básica** | Cadastro (nome, nome de usuário, e-mail, senha), código de 6 dígitos por e-mail, verificação, reenvio, login por e-mail ou nome de usuário, JWT, limite de tentativas | Conta não verificada não entra; código expira; tentativas limitadas. |
| **B3 Sessão e Google** | Refresh com rotação e detecção de reuso, logout, recuperação de senha, login com Google com vinculação por e-mail | Reuso de refresh revoga a sessão; token Google inválido retorna 401. |
| **B4 Cadastros** | Serviços, clientes (busca, normalização, duplicidade, perfil) e tipos de desconto | Teste de isolamento em todo endpoint; duplicado retorna `CLIENTE_DUPLICADO`. |
| **B5 Ciclos** | Configuração com vigência, `CicloCalculator`, geração sob demanda, listagem | Tabela de casos: calendário e rolante, virada de mês e ano, ano bissexto, troca de configuração (RN10 a RN13). |
| **B6 Lançamentos e descontos** | Criar, editar e excluir; comissão; descontos em R$ ou %; bloqueio de data futura e de ciclo fechado | RN01 a RN09 e RN18 a RN20 cobertas por testes. |
| **B7 Fechamento** | Fechar e reabrir, totais gravados, descontos do ciclo | Teste de concorrência: lançar durante o fechamento. |
| **B8 Dashboard** | Ciclo atual, comparação com o anterior, card acumulado | Números conferem com dados de teste conhecidos. |
| **B9 Relatórios** | Relatórios prontos, construtor com lista fechada, listagem de clientes | Nenhuma entrada do usuário concatenada em SQL; todo filtro inclui `usuario_id`. |
| **B10 Exportação** | CSV e PDF do relatório com os filtros aplicados | Arquivo reflete exatamente o que a tela mostra. |
| **B11 Endurecimento e deploy** | Dockerfile multi-stage, CORS e headers, revisão de segurança, README, deploy em homologação | Aplicação rodando em homologação com migrations automáticas. |

### 2.5 Detalhamento das fases do back-end

Cada fase traz objetivo, requisitos cobertos, tarefas na ordem de execução, testes e critério de aceite. Limites e tempos citados são pontos de partida e ficam em configuração.

#### B0 — Fundação

**Objetivo.** Ambiente reproduzível, qualidade automatizada e base de erros e segurança prontas antes da primeira regra de negócio.

**Requisitos:** RNF de segurança, manutenibilidade e observabilidade. **Depende de:** nada.

**Tarefas**

1. Criar o monorepo (`backend/`, `frontend/`, `docs/`) com `README`, `.gitignore`, `.editorconfig` e proteção da branch `main`.
2. Gerar o projeto no Spring Initializr: Java 21, Maven, Spring Boot 4spring, com Web, Validation, Data JPA, PostgreSQL, Flyway, Security, OAuth2 Resource Server, Actuator, Mail e springdoc-openapi; Testcontainers nos testes.
3. Criar o pacote base e o pacote `common` (config, security, erro, tempo, util).
4. `docker-compose.yml` com `postgres:16` (volume nomeado e healthcheck) e `mailpit` (SMTP 1025, interface 8025).
5. Perfis `dev`, `test` e `prod` em `application-*.yml`, com as variáveis `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `CODE_PEPPER`, `GOOGLE_CLIENT_ID`, `MAIL_HOST`, `MAIL_PORT`, `APP_TIMEZONE`, `CORS_ORIGINS` e `APP_BASE_URL`; `.env.example` versionado e `.env` ignorado.
6. Flyway ativo e `ddl-auto=validate`; a primeira migration vem em B1.
7. Bean `Clock` com o fuso de `APP_TIMEZONE` (padrão `America/Recife`) e um `TempoService` com `hoje()`.
8. Erros: enum `ErrorCode`, `ApiException` e `GlobalExceptionHandler` (`@RestControllerAdvice`) respondendo `ProblemDetail` com `code`, `detail` e, na validação, `fields[]` (campo e mensagem). Tratar validação, violação de constraint, acesso negado e erro genérico (500 sem expor stack trace).
9. Segurança base: `SecurityFilterChain` stateless, CSRF desabilitado (a API usa token no header), CORS restrito a `CORS_ORIGINS`, headers de segurança, `permitAll` somente em `/api/v1/auth/**`, `/actuator/health` e documentação (apenas em dev); todo o resto autenticado.
10. Logs em JSON (logstash-logback-encoder); filtro que gera `X-Request-Id` e o coloca no MDC; mascarar `senha`, `codigo` e `token`.
11. springdoc: grupo `v1`, esquema bearer e geração do `backend/openapi.json` no build (plugin do springdoc na fase `integration-test`), que o front consome.
12. Qualidade: Spotless, Checkstyle, JaCoCo e a classe base `AbstractIntegrationTest` com container PostgreSQL reutilizável.
13. GitHub Actions `backend.yml`: Java 21, cache Maven, `mvn verify`, publicação do `openapi.json` e do relatório JaCoCo.

**Testes**

- O contexto sobe com Testcontainers.
- `GET /actuator/health` responde 200 sem autenticação; rota protegida sem token retorna 401; rota inexistente retorna 404 em `ProblemDetail`.
- Erro de validação retorna 400 com `fields[]`; exceção inesperada retorna 500 sem stack trace.

**Critério de aceite**

- A partir de um clone limpo, `docker compose up -d` e `./mvnw spring-boot:run` sobem o ambiente seguindo apenas o README.
- CI verde em pull request, com `openapi.json` gerado.

**Status:** concluída. `docker compose config`, `docker compose up -d`,
`./mvnw test` e `./mvnw verify -DskipTests -U` foram executados com sucesso;
o build gerou `api/openapi.json` e o contrato de health, autenticação e 404 foi
validado por testes de integração.

#### B1 — Modelo de dados

**Objetivo.** Esquema completo e versionado, com integridade garantida pelo banco como segunda linha de defesa.

**Requisitos:** modelo da seção 7 do documento de requisitos; RN03, RN08, RN19, RN21, RN24, RN25. **Depende de:** B0.

**Tarefas**

1. Criar as migrations Flyway em `db/migration` (`V{n}__descricao.sql`), uma por assunto, sem editar as já aplicadas; correções entram em novas versões.
2. Habilitar a extensão `btree_gist` (necessária para a restrição de ciclos).
3. Criar as tabelas conforme a tabela abaixo.
4. Em cada tabela filha, usar **chave estrangeira composta** `(usuario_id, x_id)` apontando para `pai(usuario_id, id)` (com `UNIQUE (usuario_id, id)` no pai). Assim o banco garante que lançamento, desconto e ciclo só referenciem registros do mesmo usuário.
5. Mapear as entidades JPA: `BaseEntity` (id UUID gerado pela aplicação, `criadoEm` e `atualizadoEm` por Spring Data Auditing), `@Version` em ciclo e lançamento, enums como `STRING` e relacionamentos `LAZY`.
6. Convenção nos repositórios: nenhum `findById` puro em recurso de usuário; sempre métodos `...AndUsuarioId`.

| Migration | Tabela | Colunas e restrições |
| --- | --- | --- |
| V1 | `usuario` | id UUID PK; nome VARCHAR(120); username VARCHAR(30); email VARCHAR(254); email\_verificado BOOLEAN default false; senha\_hash VARCHAR(100) nulo; google\_id VARCHAR(64) nulo e único; criado\_em e atualizado\_em TIMESTAMPTZ. `UNIQUE` em username e em email (gravados em minúsculas); `CHECK` username `~ '^[a-z0-9._]{3,30}$'`; `CHECK` senha\_hash ou google\_id preenchido. |
| V2 | `codigo_verificacao` | id; usuario\_id; tipo (`VERIFICACAO_EMAIL` ou `RECUPERACAO_SENHA`); codigo\_hash CHAR(64); expira\_em; tentativas SMALLINT default 0; usado\_em nulo; criado\_em. Índice `(usuario_id, tipo, criado_em desc)`. |
| V2 | `refresh_token` | id; usuario\_id; familia\_id UUID; token\_hash CHAR(64) único; expira\_em; revogado\_em nulo; substituido\_por UUID nulo; criado\_em. Índices em familia\_id e usuario\_id. |
| V3 | `configuracao_ciclo` | id; usuario\_id; tipo (`SEMANAL`, `QUINZENAL`, `MENSAL`); contagem (`CALENDARIO`, `A_PARTIR_DE_DATA`); data\_inicial DATE nula; vigente\_a\_partir\_de DATE. `UNIQUE (usuario_id, vigente_a_partir_de)`; `CHECK` data\_inicial obrigatória quando a contagem é `A_PARTIR_DE_DATA`. |
| V3 | `ciclo` | id; usuario\_id; data\_inicio e data\_fim DATE; status (`ABERTO`, `FECHADO`) default `ABERTO`; fechado\_em nulo; total\_bruto, total\_comissao, total\_descontos\_lancamentos, total\_descontos\_ciclo e total\_liquido NUMERIC(14,2) nulos (preenchidos só no fechamento); versao BIGINT. `CHECK` data\_fim >= data\_inicio; `UNIQUE (usuario_id, data_inicio)`; `EXCLUDE USING gist (usuario_id WITH =, daterange(data_inicio, data_fim, '[]') WITH &&)` impede ciclos sobrepostos do mesmo usuário. |
| V4 | `servico` | id; usuario\_id; nome VARCHAR(100); valor\_sugerido NUMERIC(12,2) nulo com `CHECK` > 0; ativo BOOLEAN default true; criado\_em. |
| V4 | `cliente` | id; usuario\_id; nome VARCHAR(120); nome\_normalizado VARCHAR(120); telefone VARCHAR(20) nulo; observacao VARCHAR(500) nula; ativo; criado\_em. `UNIQUE (usuario_id, nome_normalizado)`, valendo também para inativos (RN21, RN22). |
| V4 | `tipo_desconto` | id; usuario\_id; nome VARCHAR(60); ativo; criado\_em. |
| V5 | `lancamento` | id; usuario\_id; ciclo\_id; servico\_id; servico\_nome VARCHAR(100) (snapshot, RN03); cliente\_id **não nulo** (RN19); data DATE; valor\_bruto NUMERIC(12,2) com `CHECK` > 0; percentual NUMERIC(5,2) com `CHECK` entre 0 e 100; valor\_comissao NUMERIC(12,2) com `CHECK` >= 0; observacao VARCHAR(500) nula; excluido\_em TIMESTAMPTZ nulo (exclusão lógica); criado\_em; atualizado\_em; versao BIGINT. |
| V5 | `desconto` | id; usuario\_id; tipo\_desconto\_id; lancamento\_id nulo; ciclo\_id nulo; modo (`VALOR`, `PERCENTUAL`); valor\_informado NUMERIC(12,2) com `CHECK` > 0; valor\_calculado NUMERIC(12,2) com `CHECK` >= 0; descricao VARCHAR(200) nula. `CHECK` de exatamente um entre lancamento\_id e ciclo\_id preenchido; `CHECK` valor\_informado <= 100 quando o modo é `PERCENTUAL`. |
| V6 | índices | `lancamento (usuario_id, data) WHERE excluido_em IS NULL`; `lancamento (usuario_id, cliente_id)`; `lancamento (usuario_id, ciclo_id)`; `lancamento (usuario_id, servico_id)`; `desconto (lancamento_id)`; `desconto (ciclo_id)`. |

**Testes**

- As migrations aplicam do zero no Testcontainers e o Hibernate valida o mapeamento sem diferenças.
- Uma violação esperada por restrição: username com maiúscula, e-mail duplicado, percentual 100,01, valor bruto 0, desconto sem alvo e com os dois alvos, nome normalizado repetido por usuário, ciclos sobrepostos e referência a registro de outro usuário (chave composta).
- Ciclos adjacentes (um termina no dia 15, outro começa no 16) e o mesmo nome normalizado em usuários diferentes são aceitos.

**Critério de aceite**

- Esquema completo criado por migrations, sem `ddl-auto` gerando nada.
- Toda regra de integridade da lista de testes falha no banco, mesmo sem passar pela aplicação.

#### B2 — Autenticação básica

**Objetivo.** Cadastro com e-mail verificado e login por e-mail ou nome de usuário.

**Requisitos:** RF01, RF02, RF04; RN25, RN26, RN29 a RN31. **Depende de:** B1.

**Tarefas**

1. Infraestrutura de credenciais: `PasswordEncoder` BCrypt (força 12), `SecureRandom` para códigos e HMAC-SHA256 com `CODE_PEPPER` para guardar o hash do código.
2. `POST /api/v1/auth/register` com `{nome, username, email, senha}`. Validação: nome de 2 a 120 caracteres; username convertido para minúsculas e validado por `^[a-z0-9._]{3,30}$`; e-mail válido (até 254), em minúsculas; senha de 8 a 72 caracteres (limite do BCrypt). Cria o usuário com `email_verificado=false`, gera o código de 6 dígitos (com zeros à esquerda, validade de 15 min), grava só o hash, envia o e-mail e responde 201 com `{email}`, sem token. Conflitos: 409 `USERNAME_EM_USO` e 409 `EMAIL_EM_USO` (a enumeração de e-mail no cadastro é aceita pela usabilidade e mitigada pelo limite de tentativas).
3. Endpoints de apoio ao cadastro: `GET /auth/username/disponivel?valor=` (retorna `{disponivel}`) e `GET /auth/username/sugestao?nome=` (retorna um username livre: nome sem acentos, minúsculo, espaços viram ponto, com sufixo numérico crescente se ocupado, ex.: `joao.silva`, `joao.silva2`).
4. `POST /auth/verify-email` com `{email, codigo}`: pega o código vigente e não usado, compara o hash em tempo constante; cada erro incrementa `tentativas` e, no quinto, o código é invalidado. Sucesso: `usado_em`, `email_verificado=true` e devolve o `accessToken` (login automático). Erros: 400 `CODIGO_INVALIDO`, `CODIGO_EXPIRADO` e `TENTATIVAS_EXCEDIDAS`.
5. `POST /auth/resend-code` com `{email}`: responde 202 sempre; só envia se a conta existe e não está verificada; intervalo mínimo de 60 s entre envios e no máximo 5 por hora por e-mail; invalida os códigos anteriores.
6. `POST /auth/login` com `{identificador, senha}`: identificador com `@` é e-mail, caso contrário nome de usuário (ambos em minúsculas). A senha é conferida primeiro (com o mesmo custo de BCrypt mesmo quando o usuário não existe, para não vazar por tempo): 401 `CREDENCIAIS_INVALIDAS` genérico; senha correta com e-mail não verificado retorna 403 `EMAIL_NAO_VERIFICADO`. Sucesso: `accessToken` e `expiraEm`.
7. JWT HS256 com `JWT_SECRET` (mínimo de 32 bytes): `sub` = id do usuário, `iat`, `exp` (15 min) e `jti`. `UsuarioAtual.id()` lê o `SecurityContext` e é a única origem do usuário em qualquer service (RN29).
8. `EmailSender` (interface), `SmtpEmailSender` com `JavaMailSender` e templates simples em pt-BR; envio `@Async` com executor próprio e três tentativas com backoff; falhas logadas sem dados sensíveis.
9. Limite de tentativas (Bucket4j em memória, instância única; Redis fica para quando houver mais de uma): login 10 por minuto por IP e por identificador; cadastro 5 por hora por IP; verificação 10 por 15 min por e-mail. Excesso retorna 429 `LIMITE_EXCEDIDO` com `Retry-After`.
10. Job agendado diário que apaga códigos expirados há mais de 7 dias.

**Testes**

- Unitários: gerador de código (6 dígitos, com zeros à esquerda), sugestão de username (acentos, colisão), regex de username.
- Integração do fluxo cadastro → verificação → login, capturando o código por um `EmailSender` de teste.
- Conta não verificada não loga; código expirado; cinco erros invalidam o código; cooldown e limite de reenvio.
- Duplicidade sem diferenciar maiúsculas em username e e-mail; resposta 429 com `Retry-After`.
- O banco guarda só o hash do código, e senha e código nunca aparecem nos logs (captura de log no teste).

**Critério de aceite**

- Um usuário novo se cadastra, recebe o código no Mailpit, verifica e entra, pelo Swagger e pelos testes de integração.
- Nenhuma resposta de erro permite distinguir senha errada de usuário inexistente.

#### B3 — Sessão persistente, Google e recuperação de senha

**Objetivo.** Sessão que dura sem pedir novo código, login com Google e recuperação de senha por e-mail.

**Requisitos:** RF03, RF05, RF06; RN27, RN28, RN30, RN31. **Depende de:** B2.

**Tarefas**

1. Refresh token: 32 bytes aleatórios em Base64url, guardado como SHA-256. Cada login, verificação ou login Google abre uma nova **família** (`familia_id`); validade de 30 dias.
2. Cookies emitidos em login, verificação, Google e refresh: `refresh_token` (`HttpOnly`, `Secure`, `SameSite=Strict`, `Path=/api/v1/auth`) e `sessao_ativa=1` (sem `HttpOnly`, `Path=/`, mesma validade), que serve só para o middleware do front redirecionar páginas e não contém nada sensível. Em dev sem HTTPS, o atributo `Secure` é configurável.
3. `POST /auth/refresh` (usa o cookie): valida existência, validade e `revogado_em`; emite novo access token e novo refresh (rotação) e marca o anterior com `substituido_por`. Se um refresh já substituído for apresentado (reuso), **revoga a família inteira** e responde 401 `SESSAO_INVALIDA`. Confere também o header `Origin` contra `CORS_ORIGINS`.
4. `POST /auth/logout`: revoga a família, expira os dois cookies e responde 204 (idempotente).
5. `POST /auth/forgot-password` com `{email}`: responde 202 sempre. Se a conta existe, está verificada e tem senha, gera um código `RECUPERACAO_SENHA` (6 dígitos, 15 min) e envia; contas somente Google não recebem e-mail.
6. `POST /auth/reset-password` com `{email, codigo, novaSenha}`: mesmas regras de tentativas do B2; grava o novo `senha_hash`, **revoga todas as famílias de refresh** do usuário e responde 204.
7. `POST /auth/google` com `{idToken}`: validar com `NimbusJwtDecoder` e o JWKS `https://www.googleapis.com/oauth2/v3/certs` (com cache), exigindo `iss` igual a `accounts.google.com` ou `https://accounts.google.com`, `aud` igual a `GOOGLE_CLIENT_ID`, `exp` válido e `email_verified=true`; caso contrário, 401 `TOKEN_GOOGLE_INVALIDO`. Resolução da conta: (a) existe `google_id`: entra; (b) existe o e-mail: **vincula**, gravando `google_id` e `email_verificado=true`, e, se a conta local nunca foi verificada, descarta o `senha_hash` (impede que quem cadastrou um e-mail alheio mantenha acesso); (c) não existe: cria o usuário com nome do token, username sugerido, e-mail verificado e sem senha. A resposta inclui `novoUsuario` e `cicloConfigurado`.

**Testes**

- Rotação do refresh; reuso de um refresh antigo revoga a família; refresh expirado; logout invalida a sessão; atributos dos cookies; `Origin` inválido recusado.
- `forgot-password` responde 202 mesmo para e-mail inexistente; `reset-password` revoga as sessões; código errado, expirado e tentativas.
- Google com um servidor JWKS falso (WireMock) e tokens válidos e inválidos (`aud`, `iss`, `exp`, `email_verified=false`); vinculação por e-mail; descarte da senha de conta não verificada; criação de conta nova.

**Critério de aceite**

- O usuário permanece logado por 30 dias sem novo código; o reuso de um refresh derruba a sessão.
- Login com Google cria ou vincula a conta corretamente, e a recuperação de senha funciona de ponta a ponta no Mailpit.

#### B4 — Cadastros: conta, serviços, tipos de desconto e clientes

**Objetivo.** Dados pessoais do usuário e a regra de duplicidade de clientes.

**Requisitos:** RF08, RF14, RF15, RF16 (lado do back), RF17, RF18; RN21 a RN25, RN29. **Depende de:** B3.

**Tarefas**

1. Conta: `GET /usuarios/me` retorna `{id, nome, username, email, temSenha, temGoogle, cicloConfigurado}`; `PUT /usuarios/me` com `{nome, username}` (mesmas regras e unicidade do cadastro; 409 `USERNAME_EM_USO`). Alterar e-mail e trocar senha estando logado não fazem parte desta versão.
2. Serviços: `GET /servicos?ativo=&busca=&page=&size=` (ordem alfabética; sem `ativo` retorna todos), `POST /servicos` com `{nome, valorSugerido?}`, `PUT /servicos/{id}` e `PATCH /servicos/{id}/ativo` com `{ativo}`. Sem `DELETE` (RN24). Nome de 1 a 100 caracteres; valor sugerido maior que zero, com 2 casas.
3. Tipos de desconto: os mesmos quatro endpoints em `/tipos-desconto` (nome de 1 a 60 caracteres).
4. `NomeNormalizador.normalizar(String)`: `Normalizer.NFD`, remoção de marcas diacríticas (`\p{M}`), `toLowerCase(Locale.ROOT)`, `trim` e colapso de espaços repetidos. Resultado fixo em teste: “Maria das Graças” e “maria das gracas” viram `maria das gracas`; “Graça Maria” vira `graca maria` (outro cliente).
5. Clientes: `GET /clientes?busca=&ativo=&page=&size=` (o termo é normalizado e comparado por `nome_normalizado LIKE %termo%`; ordem alfabética; `size` padrão 20, máximo 50), `POST /clientes` com `{nome, telefone?, observacao?}`, `PUT /clientes/{id}` e `PATCH /clientes/{id}/ativo`. Telefone: dígitos, espaço, parênteses, `+` e `-`, até 20 caracteres.
6. Duplicidade (RN21, RN22): ao criar ou renomear, calcular `nome_normalizado` e consultar `(usuario_id, nome_normalizado)`, incluindo inativos. Se existir, 409 `CLIENTE_DUPLICADO` com `clienteExistente: {id, nome, ativo}` no corpo, para o front oferecer “usar este cliente” ou “reativar”. A violação da constraint única (corrida entre duas requisições) é capturada e vira a mesma resposta.
7. Isolamento: todo service obtém o usuário de `UsuarioAtual`; recurso de outro usuário responde 404 `RECURSO_NAO_ENCONTRADO`.
8. O perfil e o histórico do cliente entram em B6, quando os lançamentos já existem.

**Testes**

- Normalizador em tabela de exemplos (acentos, maiúsculas, espaços, ordem de palavras).
- Duplicidade: caixa e acento diferentes bloqueiam; ordem diferente de palavras é aceita; cliente inativo também bloqueia; renomear para um nome existente bloqueia; o mesmo nome em outro usuário é aceito.
- Corrida: duas threads criando o mesmo cliente resultam em um 201 e um 409.
- Teste de isolamento parametrizado por endpoint: o usuário B recebe 404 em recurso do usuário A e nunca vê dados dele nas listagens.
- Paginação respeita o tamanho máximo; desativar e reativar funciona.

**Critério de aceite**

- Serviços, tipos de desconto e clientes operam de ponta a ponta com isolamento comprovado por teste.
- Nenhum cliente duplicado por nome normalizado pode ser criado, nem por requisições simultâneas.

#### B5 — Ciclos de pagamento

**Objetivo.** Calcular períodos, versionar a configuração e criar ciclos sob demanda sem sobreposição.

**Requisitos:** RF07, RF09, RF10, RF11; RN10 a RN13. **Depende de:** B4.

**Tarefas**

1. `CicloCalculator` (classe pura): `Periodo calcular(ConfiguracaoCiclo cfg, LocalDate data)`.
   - Calendário semanal: início na segunda-feira (`previousOrSame(MONDAY)`), fim = início + 6.
   - Calendário quinzenal: dia 1 a 15 ou 16 ao último dia do mês. Calendário mensal: dia 1 ao último dia.
   - A partir de uma data (âncora), semanal e quinzenal: blocos de 7 e 15 dias, com `n = floorDiv(dias entre a âncora e a data, tamanho)` e início = âncora + n × tamanho.
   - A partir de uma data, mensal: `n` = meses inteiros entre a âncora e a data; início = `âncora.plusMonths(n)` e fim = `âncora.plusMonths(n + 1).minusDays(1)`. Tudo calculado a partir da âncora, nunca encadeando ciclos.
   - Datas anteriores à âncora funcionam (`n` negativo).
2. Resolução da configuração por data: vale a de maior `vigente_a_partir_de` menor ou igual à data; se a data for anterior a todas, vale a mais antiga. **Corte na troca:** se a configuração não é a mais antiga, o início do ciclo nunca é anterior ao `vigente_a_partir_de` dela, então o primeiro ciclo da nova regra pode ser mais curto. Isso evita ciclos sobrepostos; a restrição `EXCLUDE` do B1 é a defesa final.
3. `PUT /usuarios/me/ciclo-config` com `{tipo, contagem, dataInicial?}` (`dataInicial` obrigatória em `A_PARTIR_DE_DATA` e ignorada no calendário):
   - Sem configuração (primeiro acesso): grava com `vigente_a_partir_de` = hoje.
   - Com configuração: grava uma nova linha com `vigente_a_partir_de` = fim do ciclo atual + 1 dia e substitui uma configuração futura pendente, se houver. O ciclo atual e os fechados não mudam (RN13).
   - `GET /usuarios/me/ciclo-config` retorna a vigente hoje e a pendente.
   - `GET /usuarios/me/ciclo-config/previa?tipo=&contagem=&dataInicial=` retorna o ciclo de hoje e os dois seguintes, para o front mostrar o efeito sem duplicar a lógica.
4. `CicloService.obterOuCriar(usuarioId, data)`: calcula o período, executa `INSERT ... ON CONFLICT (usuario_id, data_inicio) DO NOTHING` e relê, tratando a corrida entre requisições simultâneas.
5. `GET /ciclos?status=&page=&size=` (mais recentes primeiro; garante a existência do ciclo atual), `GET /ciclos/atual` e `GET /ciclos/{id}`. Ciclo aberto calcula os totais na hora a partir dos lançamentos; ciclo fechado usa os totais gravados no fechamento (B7).

**Testes** (com `Clock` fixo, em tabela de casos)

- Calendário semanal: 04/11/2026 (quarta) → 02/11 a 08/11; domingo 08/11 ainda no mesmo ciclo; segunda 09/11 → 09/11 a 15/11.
- Calendário quinzenal: 04/11/2026 → 01/11 a 15/11; 16/11 → 16/11 a 30/11; 20/02/2028 (bissexto) → 16/02 a 29/02. Mensal: 31/01 → 01/01 a 31/01.
- Quinzenal a partir de 04/11/2026: 18/11 → 04/11 a 18/11; 19/11 → 19/11 a 03/12; data 03/11 (anterior à âncora) → 20/10 a 03/11.
- Mensal a partir de 31/01/2026: 27/02 → 31/01 a 27/02; 28/02 → 28/02 a 30/03; 31/03 → 31/03 a 29/04.
- Troca de configuração: ciclo calendário quinzenal 01/11 a 15/11 e nova regra semanal com âncora 10/11 vigente de 16/11: a data 16/11 resulta em 16/11 a 16/11 (corte) e 17/11 em 17/11 a 23/11.
- Configuração pendente substituída por outra; ciclo atual intacto após a troca.
- Duas threads chamando `obterOuCriar` para a mesma data geram um único ciclo; teste de isolamento por endpoint.

**Critério de aceite**

- Todos os casos acima passam e nenhum cenário de troca gera ciclos sobrepostos.
- O primeiro acesso define o ciclo e `cicloConfigurado` passa a `true`.

#### B6 — Lançamentos e descontos

**Objetivo.** Registrar, editar, excluir e consultar lançamentos com comissão e descontos corretos, respeitando ciclos fechados.

**Requisitos:** RF20 (dados do perfil), RF21, RF23, RF24, RF25; RN01 a RN09, RN15, RN18 a RN20, RN23, RN24. **Depende de:** B5.

**Tarefas**

1. Classes puras: `CalculadoraComissao.comissao(bruto, percentual)` = `bruto × percentual ÷ 100` em **uma única** divisão com 2 casas e `HALF_UP`; `CalculadoraDesconto.valor(modo, informado, comissao)` (R$: o próprio valor; %: `comissao × informado ÷ 100`, 2 casas, `HALF_UP`); `liquido(comissao, descontos)`; e a checagem de que a soma dos descontos não passa da comissão.
2. `POST /lancamentos` com `{servicoId, clienteId, data, valorBruto, percentual, observacao?, descontos[{tipoDescontoId, modo, valor, descricao?}]}`. Em uma transação: (1) validar posse de serviço, cliente e tipos (404) e que estão ativos (422 `SERVICO_INATIVO`, `CLIENTE_INATIVO`, `TIPO_DESCONTO_INATIVO`); (2) `data` menor ou igual a `hoje()` (422 `DATA_FUTURA`); (3) `obterOuCriar(data)` e bloqueio compartilhado na linha do ciclo; (4) ciclo `FECHADO` retorna 409 `CICLO_FECHADO` com o `cicloId`; (5) calcular comissão e descontos, e soma maior que a comissão retorna 422 `DESCONTO_EXCEDE_BASE`; (6) gravar o lançamento (com o nome do serviço copiado) e os descontos. Resposta 201 com `{id, data, servico, cliente, valorBruto, percentual, comissao, descontos[{id, tipo, modo, valorInformado, valorCalculado, descricao}], totalDescontos, liquido, ciclo{id, inicio, fim}, versao}`.
3. `PUT /lancamentos/{id}` com o mesmo corpo mais `versao`: só edita se o ciclo atual do lançamento e o ciclo de destino (quando a data muda de ciclo) estão abertos; recalcula tudo e substitui os descontos; versão desatualizada retorna 409 `CONFLITO_VERSAO`. O nome copiado do serviço só muda se o serviço for trocado.
4. `DELETE /lancamentos/{id}`: preenche `excluido_em`, exige ciclo aberto e responde 204. Todas as consultas ignoram excluídos.
5. `GET /lancamentos?de=&ate=&cicloId=&servicoId=&clienteId=&page=&size=` (tamanho padrão 20, máximo 100; ordem `data desc, criado_em desc`). Cada item traz os mesmos campos do POST e a resposta inclui `resumo` de todo o filtro (quantidade, soma de bruto, comissão, descontos e líquido). Evitar N+1: buscar os descontos da página em uma segunda consulta por `IN`. Também `GET /lancamentos/{id}`.
6. Perfil do cliente: `GET /clientes/{id}` passa a incluir `resumo` (total de atendimentos, comissão gerada, primeira e última visita) e `GET /clientes/{id}/lancamentos` (paginado, mesmo formato), por consulta agregada apoiada no índice `(usuario_id, cliente_id)`.
7. Vetores compartilhados em `docs/test-vectors/comissao.json`, usados também pelo front. Comissão: (100,00; 33,33) → 33,33; (150,00; 40) → 60,00; (19,99; 12,5) → 2,50 (2,49875 arredonda para cima); (0,01; 50) → 0,01 (0,005 arredonda para cima); (99,99; 33,33) → 33,33. Desconto: comissão 60,00 com 10% → 6,00; comissão 2,50 com 33,33% → 0,83; soma de descontos 60,01 sobre comissão 60,00 → recusado.

**Testes**

- Vetores de comissão e de desconto; valores de borda (percentual 0 e 100, centavos).
- Data futura com `Clock` fixo, provando que “hoje” usa o fuso configurado (23h30 em Recife já é o dia seguinte em UTC).
- Posse de serviço, cliente e tipo de outro usuário; inativos; ciclo fechado em criar, editar e excluir; mover lançamento entre ciclos; vários descontos; excedente; conflito de versão.
- Paginação e `resumo` conferem com a soma manual; número de queries por listagem limitado (estatísticas do Hibernate); isolamento por endpoint.

**Critério de aceite**

- Lançamentos são criados, editados, excluídos e listados com valores idênticos aos vetores de teste.
- Nenhuma operação altera lançamento de ciclo fechado.

#### B7 — Fechamento e reabertura de ciclo

**Objetivo.** Fechar e reabrir ciclos com totais corretos e sem corrida com lançamentos.

**Requisitos:** RF11 (totais), RF12, RF13; RN07, RN08, RN14 a RN17. **Depende de:** B6.

**Tarefas**

1. `POST /ciclos/{id}/fechar` com `{descontos[{tipoDescontoId, modo, valor, descricao?}]}` (lista completa, que substitui os descontos de ciclo existentes). Em uma transação:
   - bloqueio exclusivo na linha do ciclo; o status deve ser `ABERTO`, senão 409 `CICLO_JA_FECHADO`;
   - carregar os lançamentos não excluídos e calcular `totalBruto`, `totalComissao`, `totalDescontosLancamentos` e `baseLiquida = totalComissao − totalDescontosLancamentos`;
   - calcular cada desconto do ciclo (R$ direto; % sobre `baseLiquida`, 2 casas, `HALF_UP`); soma maior que a base retorna 422 `DESCONTO_EXCEDE_BASE`;
   - gravar os descontos do ciclo, `totalDescontosCiclo`, `totalLiquido = baseLiquida − totalDescontosCiclo`, `status=FECHADO` e `fechado_em`, incrementando `versao`. Pode fechar antes da data final e o ciclo mantém as datas originais (RN14); ciclo sem lançamentos fecha com totais zero.
2. `POST /ciclos/{id}/reabrir`: bloqueio exclusivo; exige `FECHADO` (409 `CICLO_NAO_FECHADO`); volta a `ABERTO`, limpa os totais gravados e mantém os descontos do ciclo, que aparecem pré-preenchidos no próximo fechamento. Qualquer ciclo fechado pode ser reaberto (RN16), sem mudar qual é o ciclo atual.
3. `GET /ciclos/{id}` retorna o resumo, os descontos do ciclo (com valor calculado) e os totais (gravados se fechado, calculados na hora se aberto).
4. O fechamento recalcula tudo do zero, sem confiar em totais incrementais. Ordem fixa de bloqueios (ciclo, depois lançamentos) para evitar deadlock, registrada no código.

**Testes**

- Cenário numérico completo: lançamento A (100,00 a 40% = 40,00, com desconto de 10% = 4,00), B (80,50 a 50% = 40,25, com desconto de R$ 5,00) e C (200,00 a 30% = 60,00). Esperado: bruto 380,50; comissão 140,25; descontos dos lançamentos 9,00; base 131,25; desconto de ciclo de 5% = 6,56; líquido 124,69.
- Fechar antes do fim mantém as datas; fechar duas vezes retorna 409; reabrir e fechar de novo recalcula; reabrir ciclo antigo com ciclos posteriores fechados; desconto de ciclo acima da base é recusado.
- Concorrência: várias threads criam lançamentos enquanto outra fecha o ciclo. Invariantes: todo lançamento gravado antes do fechamento está nos totais, os demais recebem `CICLO_FECHADO`, e nenhum lançamento fica em ciclo fechado fora dos totais.
- Isolamento por endpoint.

**Critério de aceite**

- Os totais gravados batem com o cenário numérico e com a soma dos lançamentos.
- O teste de concorrência passa de forma repetida (executado centenas de vezes no CI).

#### B8 — Dashboard

**Objetivo.** Indicadores do ciclo atual, comparação, evolução e acumulado, vindos de uma fonte única.

**Requisitos:** RF26, RF27, RF28. **Depende de:** B7.

**Definições usadas nos cálculos**

- Comissão do ciclo = soma da comissão dos lançamentos; descontos = soma dos descontos dos lançamentos (e, em ciclo fechado, também os descontos do ciclo); líquido = comissão − descontos. Em ciclo aberto contam só os descontos dos lançamentos; os descontos do ciclo passam a contar no fechamento.
- Ticket médio = valor bruto total ÷ quantidade de lançamentos. Serviço mais rentável e cliente que mais gerou comissão = maior soma de comissão (antes dos descontos), com desempate por nome em ordem alfabética.
- Clientes ativos = clientes com `ativo = true`. Ciclos fechados = quantidade de ciclos com status `FECHADO`.

**Tarefas**

1. `GET /dashboard?ciclos=6` (padrão 6, máximo 24) responde `{cicloAtual, cicloAnterior, variacao, evolucao[], acumulado}`:
   - `cicloAtual` e `cicloAnterior`: `{id, inicio, fim, status, totalBruto, comissao, descontos, liquido, quantidade, ticketMedio, servicoMaisRentavel{id, nome, comissao}}` (o ciclo anterior é nulo se não existir dado);
   - `variacao`: `{comissaoPercentual, liquidoPercentual}` = (atual − anterior) ÷ anterior × 100, com 1 casa, nulo quando o anterior é zero ou não existe;
   - `evolucao`: os últimos N ciclos em ordem cronológica, `{inicio, fim, status, comissao, liquido}`, incluindo ciclos sem lançamentos com valor zero para o gráfico ter continuidade;
   - `acumulado`: `{totalComissoes, totalLancamentos, clientesAtivos, clienteTop{id, nome, comissao}, ciclosFechados}`.
2. O ciclo anterior e os da evolução são calculados com `CicloCalculator` e a resolução de configuração por data (respeita trocas de configuração), sem exigir que estejam gravados. Um único `ResumoCicloQuery` devolve o mesmo formato para qualquer período: lê os totais gravados quando o ciclo está fechado e agrega os lançamentos quando está aberto.
3. Sem carregar entidades: projeções e consultas agregadas (no máximo 5), apoiadas nos índices `(usuario_id, data)` e `(usuario_id, cliente_id)`.

**Testes**

- Massa de dados fixa com dois ciclos e valores conhecidos; todos os campos conferidos.
- Usuário sem lançamentos: zeros, `servicoMaisRentavel` e `clienteTop` nulos, ticket médio 0,00.
- Variação nula quando o anterior é zero; evolução com ciclos vazios e com troca de configuração no meio.
- Clientes inativos fora de “clientes ativos”; lançamentos excluídos ignorados; desempate determinístico; contagem de ciclos fechados.
- Isolamento entre usuários.

**Critério de aceite**

- Os números do dashboard coincidem com a soma manual e com o resumo do histórico (B6) para o mesmo período.

#### B9 — Relatórios

**Objetivo.** Um motor único de relatórios, seguro e correto, que serve os relatórios prontos, o construtor e a listagem de clientes.

**Requisitos:** RF19, RF29 a RF34. **Depende de:** B8.

**Tarefas**

1. Formato de resposta único `RelatorioResultado`: `{codigo, titulo, filtros, colunas[{codigo, rotulo, tipo}], linhas[], totais, truncado, geradoEm}`, com `tipo` em `TEXTO`, `MOEDA`, `INTEIRO`, `DATA` ou `PERCENTUAL`. Front e exportadores só consomem esse formato.
2. Listas fechadas em código: `Metrica` (`COMISSAO`, `VALOR_BRUTO`, `DESCONTOS`, `LIQUIDO`, `QUANTIDADE`, `TICKET_MEDIO`) e `Agrupamento` (`SERVICO`, `CLIENTE`, `DIA`, `SEMANA`, `MES`, `CICLO`, `TIPO_DESCONTO`). Cada valor mapeia para um fragmento SQL fixo; entrada do usuário só entra como parâmetro nomeado.
3. Construtor `GET /relatorios` com `de`, `ate`, `cicloId` (repetível), `servicoId`, `clienteId`, `agrupamento`, `metrica` (repetível, pelo menos uma), `ordenarPor`, `direcao` e `limite` (padrão 500, máximo 5.000):
   - consulta base `FROM lancamento l WHERE l.usuario_id = :uid AND l.excluido_em IS NULL` mais os filtros;
   - os descontos entram por subconsulta agregada por lançamento, para não multiplicar linhas (fan-out);
   - `SEMANA` usa `date_trunc('week', data)` (segunda-feira) e `MES` usa `date_trunc('month', data)`;
   - sem agrupamento, retorna uma única linha de total;
   - regras de validade (400 `RELATORIO_INVALIDO`): `TIPO_DESCONTO` só com a métrica `DESCONTOS`, `de` não pode ser maior que `ate`, `ordenarPor` precisa ser uma coluna do resultado;
   - descontos de ciclo (apenas de ciclos fechados) aparecem só nos agrupamentos `CICLO` e `TIPO_DESCONTO` e no total; nos demais agrupamentos o líquido considera só os descontos dos lançamentos (RN06). A tela mostra uma nota explicando isso.
4. Relatórios prontos em `GET /relatorios/predefinidos/{codigo}` (parâmetros `de`, `ate` e os próprios), sobre o mesmo motor quando possível: `servicos-comissao`, `servicos-quantidade`, `servicos-rentaveis` (parâmetro `ordem` para mais ou menos rentáveis), `clientes-top-comissao`, `clientes-top-quantidade`, `clientes-mais-atendidos`, `clientes-menos-atendidos` (só clientes ativos com pelo menos um atendimento no período), `evolucao` (parâmetro `agrupamento` entre `DIA`, `SEMANA`, `MES` e `CICLO`), `descontos-por-tipo`, `descontos-por-periodo` e `ciclos-fechados` (lê os totais gravados, RF33). Os que dependem de datas de visita têm consulta própria: `clientes-ultimos-atendimentos` (cliente, data da última visita, serviço e valor), `clientes-sem-retorno?dias=N` (última visita anterior a hoje − N; padrão 30) e `clientes-novos` (primeira visita dentro do período).
5. Listagem de clientes (RF19): `GET /relatorios/clientes?busca=&ordenarPor=&direcao=&page=&size=` com nome, atendimentos, comissão gerada e última visita (cliente sem lançamento aparece com 0 e sem data), paginada e ordenável por qualquer coluna da lista fechada.
6. Proteções: nenhuma entrada concatenada em SQL; `ordenarPor` e `direcao` validados por lista fechada; limite aplicado na consulta; `SET LOCAL statement_timeout = '5s'` nas consultas de relatório; `usuario_id` sempre no `WHERE`.

**Testes**

- Matriz parametrizada (cada métrica com cada agrupamento) contra massa de dados com resultados calculados à mão.
- Fan-out: lançamento com dois descontos não duplica bruto nem comissão.
- Descontos de ciclo só nos agrupamentos permitidos; semana começando na segunda; `limite` e `truncado`; combinações inválidas retornam 400.
- Tentativa de injeção em `ordenarPor`, `agrupamento` e `direcao` retorna 400 e não tem efeito.
- Relatórios de clientes: última visita, sem retorno com N dias, novos no período, ordenação e busca; `ciclos-fechados` reflete os totais gravados.
- Isolamento: dois usuários com dados idênticos veem só os próprios.

**Critério de aceite**

- Todas as combinações válidas retornam valores iguais aos calculados à mão, e nenhuma entrada do usuário chega ao SQL sem ser parâmetro.

#### B10 — Exportação

**Objetivo.** Arquivos CSV e PDF que reproduzem exatamente o relatório exibido na tela.

**Requisitos:** RF35. **Depende de:** B9.

**Tarefas**

1. Endpoints de exportação espelhando os de consulta: `GET /relatorios/exportar`, `GET /relatorios/predefinidos/{codigo}/exportar` e `GET /relatorios/clientes/exportar`, todos com `formato=csv|pdf` e os mesmos parâmetros. Cada um monta o mesmo `RelatorioResultado` e delega a um `Exportador` (Strategy: `CsvExportador` e `PdfExportador`). A exportação não pagina: inclui todas as linhas do filtro até 20.000; acima disso, 422 `RELATORIO_MUITO_GRANDE` pedindo um filtro mais restrito.
2. CSV: UTF-8 com BOM (acentos corretos no Excel), separador `;`, números no padrão pt-BR (vírgula decimal, sem separador de milhar), datas `dd/MM/yyyy`, cabeçalho com os rótulos das colunas e linha final de totais quando houver. Proteção contra injeção de fórmula: texto iniciado por `=`, `+`, `-` ou `@` recebe um apóstrofo no começo. Escrita em streaming.
3. PDF (OpenPDF): A4 retrato, paisagem automática quando há mais de 6 colunas; cabeçalho com título, período, filtros aplicados, nome do usuário e data e hora de geração no fuso configurado; tabela com cabeçalho repetido em cada página; moeda no formato `R$ 1.234,56`; linha de totais; rodapé “Página X de Y”; fonte embutida com suporte a acentos; quebra de texto em células longas.
4. Resposta com `Content-Disposition: attachment; filename="relatorio-<codigo>-<yyyyMMdd>.<ext>"` (ASCII), `Content-Type` correto e `Cache-Control: no-store`; erros em `ProblemDetail`.
5. Log de auditoria de cada exportação (usuário, relatório, formato e número de linhas), sem o conteúdo.

**Testes**

- CSV: BOM, separador, decimais pt-BR, acentos, linha de totais e injeção de fórmula (cliente chamado `=HYPERLINK(...)`); volume de 10 mil linhas em streaming.
- PDF: arquivo válido e, com PDFBox no teste, o texto extraído contém título, filtros, linhas e totais com acentos; paisagem acima de 6 colunas; cabeçalho repetido em várias páginas.
- Paridade: as linhas do arquivo são iguais às do JSON do mesmo relatório e dos mesmos filtros.
- Limite de exportação e isolamento.

**Critério de aceite**

- Para qualquer relatório, o CSV e o PDF exportados mostram os mesmos dados da consulta com os mesmos filtros.

#### B11 — Endurecimento e deploy

**Objetivo.** Back-end pronto para homologação e uso real, com segurança revisada, desempenho medido e documentação.

**Requisitos:** RNF de segurança, desempenho e manutenibilidade. **Depende de:** B10 (partes podem começar antes).

**Tarefas**

1. Container: Dockerfile multi-stage (build com Maven, execução em `eclipse-temurin:21-jre`), usuário não root, camadas do Spring Boot, `HEALTHCHECK`, limites de memória da JVM e `server.shutdown=graceful`.
2. Produção: perfil `prod` sem Swagger, Actuator expondo só `health` e `info`, cookies `Secure`, `server.forward-headers-strategy=framework`, CORS com origens explícitas, HSTS no proxy e segredos por variável de ambiente (cofre do provedor).
3. Banco: usuário de aplicação com privilégios mínimos (DML) separado do usuário de migração (DDL); pool Hikari dimensionado; backups automáticos com teste de restauração; migrations só para frente (padrão expandir e contrair em mudanças incompatíveis).
4. Desempenho: script de carga com 100 mil lançamentos e mil clientes em um usuário de teste; `EXPLAIN (ANALYZE)` das consultas do dashboard, histórico, relatórios e perfil do cliente; ajustar índices (ex.: trigram em `cliente.nome_normalizado` se a busca ficar lenta). Metas de partida: listagens e dashboard abaixo de 300 ms no p95 e relatórios abaixo de 2 s com esse volume.
5. Revisão de segurança pelo OWASP API Security Top 10: autorização por objeto (testes de isolamento passando em todos os endpoints), autenticação (limites, rotação e reuso), exposição excessiva de dados (só DTOs), limites de recursos (tamanho de página e de relatório), mass assignment (records de entrada), configuração (headers, CORS e Actuator), injeção (relatórios) e inventário (OpenAPI como fonte única). Dependabot, verificação de dependências (OWASP Dependency-Check) e secret scanning ativos.
6. Documentação: README (execução, variáveis e arquitetura), ADRs curtos (monólito modular, rotação de refresh, normalização de nomes, corte de ciclos, descontos em dois níveis), OpenAPI publicado e exemplos de requisição.
7. CD: workflow que constrói a imagem, publica no registry e implanta em homologação, com migrations automáticas na subida e rollback pela imagem anterior.
8. Observabilidade mínima: logs com `X-Request-Id`, métricas básicas (Micrometer) e monitoramento externo do `health`; provedor a definir.

**Testes**

- Smoke test pós-deploy (health, cadastro, verificação e login) e teste de carga leve (k6) nos endpoints principais.
- Restauração de backup verificada em ambiente de teste.

**Critério de aceite**

- Aplicação em homologação com migrations automáticas, metas de desempenho atingidas e checklist de segurança sem pendências críticas.

## 3. Front-end

### 3.1 Decisões técnicas

| Tema | Decisão | Motivo |
| --- | --- | --- |
| Base | Next.js (App Router), TypeScript estrito | Definido: PWA com Next.js. |
| UI | Tailwind CSS + shadcn/ui (Radix) | Componentes acessíveis e leves, sem biblioteca pesada. |
| Dados | TanStack Query; cliente de API tipado gerado do OpenAPI (`openapi-typescript`) | Cache, revalidação e tipos sempre alinhados ao back. |
| Formulários | React Hook Form + Zod | Validação declarativa e reutilizável. |
| Dinheiro | `big.js` com `HALF_UP`; valores trafegam como string decimal; formatação com `Intl` pt-BR | Nunca usar `number` para dinheiro. |
| Datas | `date-fns` (pt-BR); datas como `YYYY-MM-DD`, sem horário | Evita erros de fuso. |
| Gráficos e tabelas | Recharts (carregado sob demanda) e TanStack Table | Dashboard e relatórios. |
| PWA | Manifest e service worker (Serwist); cache do shell estático; sem escrita offline na v1 | Instalável no celular. |
| Sessão | Google Identity Services; token de acesso em memória; refresh em cookie `httpOnly`, `Secure`, `SameSite=Strict`; proxy same-origin via rewrites do Next (`/api/*` para o Spring) | Nenhum token em `localStorage`; sem problemas de CORS e de cookie entre domínios. |
| Testes | Vitest + Testing Library, MSW, Playwright | Unitário, componente e ponta a ponta. |
| Qualidade | ESLint, Prettier, `tsc --noEmit`, Husky + lint-staged + commitlint | Padrão automático antes do commit e no CI. |

### 3.2 Estrutura de pastas

```text
frontend/src
├── app
│   ├── (publico)   login, cadastro, verificar-email, recuperar-senha
│   └── (app)       dashboard, lancamentos/novo, historico, ciclos, servicos,
│                   relatorios, clientes/[id], configuracoes
├── features        auth, ciclos, servicos, clientes, lancamentos,
│   │               descontos, relatorios, dashboard
│   └── <feature>   components, hooks, api, schemas
├── components/ui   componentes base (shadcn)
└── lib             api-client, auth, money, datas, erros
```

### 3.3 Padrões de engenharia

- **Mobile-first:** navegação inferior, “Novo lançamento” sempre acessível, alvos de toque grandes e teclado numérico nos campos de valor.
- **Campo de cliente:** combobox com busca (debounce) e “criar novo” inline; em caso de duplicidade, mostra o cliente existente e pede para diferenciar o nome (RN22).
- **Prévia de cálculo:** função pura `calcularComissao` que espelha o back-end. Os mesmos casos de teste (JSON em `docs/test-vec``tors`) rodam nos dois lados para garantir paridade; o valor oficial vem sempre da API.
- **Erros:** o `code` do back vira mensagem em pt-BR ou erro de campo; toda tela tem estado de carregamento (skeleton), vazio e erro.
- **Sessão:** renovação silenciosa ao receber 401, com fila das requisições pendentes; o logout limpa o cache do TanStack Query.
- **Rotas protegidas:** middleware do Next (presença do cookie de sessão) mais guarda no layout `(app)`.
- **Segurança:** CSP e headers no `next.config`, sem `dangerouslySetInnerHTML`.
- **Acessibilidade:** rótulos, foco visível, contraste AA e navegação por teclado.
- **Textos:** pt-BR centralizados em um módulo, sem biblioteca de i18n na v1.
- **Desempenho:** divisão de código por rota, gráficos carregados sob demanda, meta de Lighthouse acima de 90 em PWA, desempenho e acessibilidade.

### 3.4 Visão resumida das fases

| Fase | Entregas | Critério de aceite |
| --- | --- | --- |
| **F0 Fundação** | Next + TypeScript + Tailwind + shadcn, lint, formatação e hooks, CI, cliente OpenAPI, MSW, layout base e tema | Build e testes passam no CI; tela inicial renderiza. |
| **F1 Autenticação** | Cadastro, verificação por código, login por e-mail ou nome de usuário, Google, recuperação de senha, sessão com refresh, middleware | Fluxo completo contra o back real; 401 renova a sessão sem o usuário perceber. |
| **F2 Shell e PWA** | Layout `(app)`, navegação mobile e desktop, primeiro acesso (definir ciclo), manifest e service worker | App instalável no celular. |
| **F3 Serviços e configurações** | CRUD de serviços, configuração de ciclo, tipos de desconto | Alterar o ciclo mostra o efeito a partir do próximo ciclo. |
| **F4 Clientes** | Combobox reutilizável, criação inline, perfil do cliente | Duplicidade tratada com mensagem clara. |
| **F5 Novo lançamento** | Formulário com prévia de cálculo, lista dinâmica de descontos, cliente obrigatório | Formulário utilizável com uma mão; validações claras; data futura impedida. |
| **F6 Histórico** | Lista paginada, filtros, edição e exclusão | Ciclo fechado bloqueia ações com mensagem explicando como reabrir. |
| **F7 Ciclos** | Lista, detalhe, fechamento com descontos do ciclo, reabertura | Totais exibidos iguais aos gravados pelo back. |
| **F8 Dashboard** | Ciclo atual, comparação, gráfico de evolução, card acumulado | Valores batem com os relatórios. |
| **F9 Relatórios** | Relatórios prontos, construtor, listagem de clientes, exportação em PDF e CSV | O arquivo exportado reflete os filtros da tela. |
| **F10 Acabamento** | E2E dos fluxos críticos, auditoria Lighthouse, revisão de acessibilidade, estados vazios e de erro | Fluxo cadastro → verificação → lançamento → fechamento → exportação passa no Playwright. |

### 3.5 Detalhamento das fases do front-end

Mesmo formato do back-end. Enquanto um endpoint não existe, a fase usa MSW com o contrato do `openapi.json`; ao fim de cada fatia, os mocks dão lugar ao back real.

#### F0 — Fundação

**Objetivo.** Projeto Next.js com qualidade automatizada, cliente de API tipado e base de testes.

**Requisitos:** RNF de usabilidade mobile, manutenibilidade e segurança. **Depende de:** B0 (para o `openapi.json`).

**Tarefas**

1. `create-next-app` com TypeScript, App Router, ESLint, Tailwind e diretório `src`; Node LTS fixado em `.nvmrc` e `pnpm` como gerenciador.
2. TypeScript estrito (`strict` e `noUncheckedIndexedAccess`) e alias `@/`.
3. shadcn/ui com os componentes de base: Button, Input, Label, Form, Select, Dialog, Sheet, Drawer, Tabs, Popover, Command, Calendar, Table, Card, Badge, Skeleton, Alert, Input OTP e Sonner (toasts).
4. Design tokens no Tailwind (cores, raio, espaçamento e tipografia), fonte por `next/font`, tema claro (escuro fica para depois) e contraste AA.
5. Ferramentas: ESLint (`next/core-web-vitals`, typescript-eslint e `jsx-a11y`), Prettier com plugin do Tailwind, Husky, lint-staged e commitlint.
6. `lib/env.ts` validando as variáveis com Zod (`BACKEND_URL`, `NEXT_PUBLIC_GOOGLE_CLIENT_ID`, `NEXT_PUBLIC_APP_URL`).
7. `next.config.ts`: rewrite de `/api/:path*` para `${BACKEND_URL}/api/:path*`, headers de segurança, CSP base liberando `accounts.google.com` para o login Google e `poweredByHeader: false`.
8. Cliente de API: script `pnpm gen:api` (`openapi-typescript` sobre o `openapi.json` do back) e wrapper com `openapi-fetch`; classe `ApiError {status, code, detail, fields}` e `lib/erros.ts` convertendo cada `code` em mensagem pt-BR (catálogo da seção 7).
9. Providers: TanStack Query (`staleTime` de 30 s, sem retry em erros 4xx), Toaster e tratamento global de erro inesperado.
10. MSW (navegador e Node) com handlers por feature; Vitest com Testing Library, jest-dom e jest-axe; Playwright configurado.
11. Páginas globais: layout raiz, `not-found`, `error` e `loading`.
12. GitHub Actions `frontend.yml`: `pnpm` com cache, lint, `tsc --noEmit`, Vitest e `next build`.

**Testes**

- Validação do `env` falha quando falta variável; conversão de `code` em mensagem; renderização da página inicial.
- O tipo gerado do OpenAPI compila com o `openapi.json` mais recente (o CI falha se o contrato mudar sem regenerar).

**Critério de aceite**

- Lint, tipos, testes e build passam no CI; a tela inicial renderiza em desenvolvimento e em produção.

#### F1 — Autenticação

**Objetivo.** Cadastro, verificação, login (e-mail, nome de usuário e Google), recuperação de senha e sessão persistente.

**Requisitos:** RF01 a RF06; RN25, RN26, RN28. **Depende de:** F0 e B2 a B3 (MSW até lá).

**Tarefas**

1. Rotas públicas `(publico)`: `/login`, `/cadastro`, `/verificar-email`, `/recuperar-senha` e `/redefinir-senha`, com layout simples e centrado.
2. **Cadastro:** campos nome, nome de usuário, e-mail e senha (mostrar ou ocultar, indicação de força). Ao sair do campo nome sem o username ter sido editado, buscar `GET /auth/username/sugestao`; disponibilidade do username com debounce de 400 ms e estado inline (livre, ocupado, verificando). Schemas Zod espelham as regras do back (RN25, senha de 8 a 72). Ao enviar, vai para `/verificar-email?email=...`. Erros `USERNAME_EM_USO` e `EMAIL_EM_USO` aparecem no campo correspondente; em `EMAIL_EM_USO`, links para entrar e para pedir novo código.
3. **Verificação:** campo de 6 dígitos (Input OTP), foco automático, suporte a colar, envio automático ao completar, contador de 60 s para reenviar e mensagens por `code` (`CODIGO_INVALIDO`, `CODIGO_EXPIRADO`, `TENTATIVAS_EXCEDIDAS`, com chamada para pedir novo código). Sucesso leva a `/primeiro-acesso` (F2) ou ao dashboard.
4. **Login:** um campo “e-mail ou nome de usuário” e senha; `CREDENCIAIS_INVALIDAS` como erro geral; `EMAIL_NAO_VERIFICADO` leva a `/verificar-email` com reenvio pronto; `LIMITE_EXCEDIDO` mostra o tempo de espera de `Retry-After`.
5. **Google:** script `https://accounts.google.com/gsi/client` carregado com `next/script` (`lazyOnload`); o callback envia o `credential` para `POST /auth/google`; `novoUsuario` ou `cicloConfigurado=false` leva a `/primeiro-acesso`. Se o script for bloqueado, o botão some e a mensagem orienta a usar e-mail e senha.
6. **Recuperação:** `/recuperar-senha` pede o e-mail e sempre mostra a mesma confirmação; `/redefinir-senha` recebe código, nova senha e confirmação, e volta ao login.
7. **Sessão (`lib/auth`):** o token de acesso vive só em memória (módulo e contexto), nunca em `localStorage`. `AuthProvider` tenta `POST /auth/refresh` ao carregar o app (o cookie `httpOnly` é enviado sozinho) e expõe `useSession()` com `carregando`, `autenticado` e `anonimo`. O `apiFetch` injeta o `Authorization` e, ao receber 401, faz **um único refresh compartilhado** (single-flight) e repete as requisições em fila; se o refresh falhar, limpa o cache do TanStack Query e redireciona para `/login?next=`.
8. **Middleware:** `middleware.ts` redireciona rotas de `(app)` para `/login` quando o cookie de dica `sessao_ativa` não existe, e rotas públicas para o dashboard quando existe. A proteção real continua sendo a API.
9. **Logout:** `POST /auth/logout`, limpeza do cache e redirecionamento.

**Testes**

- Schemas Zod (valores válidos e inválidos); componentes de cada formulário com MSW, nos caminhos feliz e de erro (cada `code` acima).
- Sugestão e disponibilidade de username; OTP (colar, completar, reenvio com contador).
- Single-flight: três requisições simultâneas com 401 geram um único refresh; falha do refresh redireciona e limpa o cache.
- Middleware com e sem cookie de dica; acessibilidade dos formulários (jest-axe).

**Critério de aceite**

- Fluxo completo contra o back real: cadastro, código no Mailpit, verificação, login por e-mail, por username e por Google, recuperação de senha e logout.
- Recarregar a página mantém a sessão; o token de acesso nunca aparece em armazenamento do navegador.

#### F2 — Shell do app, primeiro acesso e PWA

**Objetivo.** Estrutura de navegação para uso no celular, definição do ciclo no primeiro acesso e app instalável.

**Requisitos:** RF07; D12 (PWA). **Depende de:** F1 e B4 a B5 (`cicloConfigurado` e a prévia de ciclo).

**Tarefas**

1. Layout `(app)/layout.tsx`: guarda de sessão (`useSession`), cabeçalho e, no celular, **navegação inferior** com Dashboard, Histórico, ação central “Novo lançamento”, Relatórios e “Mais” (Serviços, Ciclos, Configurações e Sair); a partir de `md`, barra lateral. Item ativo destacado, `viewport-fit=cover` e margens de segurança (`env(safe-area-inset-bottom)`).
2. Guarda de configuração: enquanto `cicloConfigurado` for falso, qualquer rota de `(app)` redireciona para `/primeiro-acesso`.
3. **Primeiro acesso:** etapa 1 (só para conta nova do Google) para confirmar o nome de usuário sugerido (`PUT /usuarios/me`, com disponibilidade inline). Etapa 2: tipo (Semanal, Quinzenal ou Mensal, em controle segmentado) e contagem (“Alinhado ao calendário”, padrão, ou “A partir de uma data”, com seletor de data que começa em hoje). Um texto dinâmico, vindo de `GET /usuarios/me/ciclo-config/previa`, mostra o ciclo de hoje e os dois seguintes (ex.: “Seu ciclo atual: 01/11 a 15/11”). Ao salvar (`PUT /usuarios/me/ciclo-config`), vai para o dashboard.
4. **PWA:** `app/manifest.ts` (nome, `short_name`, `start_url` `/dashboard`, `display: standalone`, cores do tema e ícones 192 e 512, incluindo maskable) e ícones geradas a partir de uma arte única; metas para iOS (`apple-touch-icon`, `apple-mobile-web-app-capable`).
5. **Service worker (Serwist):** pré-cache dos arquivos estáticos do build, `NetworkFirst` para navegação com página `/offline` de contingência e `NetworkOnly` para `/api/*`, para nunca guardar dados financeiros em cache. Aviso “Nova versão disponível” com botão para atualizar.
6. **Instalação:** banner “Instalar app” (evento `beforeinstallprompt`) em “Mais” e instruções curtas para iOS (Compartilhar → Adicionar à Tela de Início).

**Testes**

- Navegação renderiza os itens certos por breakpoint; redirecionamentos da guarda de sessão e da guarda de configuração.
- Primeiro acesso: troca de tipo e contagem atualiza a prévia (MSW), data inicial só aparece em “A partir de uma data”, erros do back aparecem no formulário.
- Manifest válido (teste que lê `manifest.webmanifest`); auditoria de PWA instalável com Lighthouse no CI.

**Critério de aceite**

- O app é instalável no celular, abre em tela cheia e a navegação inferior funciona com o polegar.
- Um usuário novo termina o primeiro acesso com o ciclo definido e cai no dashboard.

#### F3 — Serviços e configurações

**Objetivo.** Cadastros pessoais do prestador e ajustes de conta, ciclo e tipos de desconto.

**Requisitos:** RF08, RF09, RF10, RF14, RF15. **Depende de:** F2 e B4 a B5.

**Tarefas**

1. Componente compartilhado **`MoneyInput`**: máscara pt-BR, estado do formulário como string decimal (`"1234.50"`), `inputMode="decimal"`, sem valores negativos, aceita colar `1.234,56` e `1234.56`, usa `big.js` para normalizar. É o único campo de dinheiro do app.
2. **`/servicos`:** lista (cartões no celular, tabela no desktop) com busca e filtro Ativos, Inativos e Todos; “Novo serviço” em Sheet com nome e valor sugerido opcional; edição no mesmo Sheet; ativar e desativar com confirmação e atualização otimista; estado vazio com chamada para cadastrar o primeiro serviço; “carregar mais” com `useInfiniteQuery`.
3. **`/configuracoes`**, em seções:
   - **Conta:** nome e nome de usuário, com disponibilidade inline (RF08).
   - **Ciclo de pagamento:** mostra a configuração vigente e o mesmo formulário do primeiro acesso, com prévia. Quando existe configuração pendente, avisa “A nova configuração começa em dd/mm; o ciclo atual continua como está”. Ao salvar uma troca, mostra a data de vigência devolvida pelo back (RN13).
   - **Tipos de desconto:** lista, criação, edição e ativação (RF15), com o mesmo componente de formulário em Sheet.
   - **Instalar app** (F2) e **Sair**.
4. Invalidação de queries ao salvar; mensagens de sucesso por toast; erros de campo vindos de `fields[]`.

**Testes**

- `MoneyInput`: digitação, colagem nos dois formatos, valor vazio, limite de casas, valor enviado como string decimal.
- Serviços: criar, editar, desativar, filtro, estado vazio; tipos de desconto: criar e ativar.
- Configuração de ciclo com configuração pendente (aviso exibido); conta com username ocupado.

**Critério de aceite**

- O usuário cadastra serviços e tipos de desconto, altera o ciclo e vê claramente a partir de quando a mudança vale.

#### F4 — Clientes

**Objetivo.** Escolher ou criar cliente sem sair do lançamento, sem duplicidade, e consultar o perfil de cada cliente.

**Requisitos:** RF16, RF17, RF18, RF20; RN21 a RN24. **Depende de:** F3 e B4 (o perfil usa também B6).

**Tarefas**

1. **`ClienteCombobox`** reutilizável (Popover + Command): busca com debounce de 250 ms, até 20 resultados, destaque do trecho encontrado sem diferenciar acentos e caixa; abre em tela cheia no celular; navegação por teclado. Quando não há correspondência exata, mostra a opção “Criar ‘texto’” no fim da lista.
2. **Criação rápida** só com o nome. Em `CLIENTE_DUPLICADO`, exibe o aviso: “Já existe a cliente ‘Maria Silva’. Selecione-a ou diferencie o nome (ex.: Maria Silva Barbosa)”, com os botões “Usar a existente” e “Continuar editando”. Se `clienteExistente.ativo` for falso, o botão vira “Reativar e usar” (`PATCH /clientes/{id}/ativo`).
3. **Perfil `/clientes/[id]`:** cabeçalho (nome, telefone, observação, selo de ativo ou inativo) e ações Editar (Sheet com nome, telefone e observação, com o mesmo tratamento de duplicidade ao renomear) e Inativar ou Reativar; cartões de resumo (atendimentos, comissão gerada, primeira e última visita); histórico paginado de lançamentos (data, serviço, valor bruto, comissão e ciclo); atalho “Novo lançamento” que já abre com o cliente escolhido (`?clienteId=`).
4. A listagem de clientes fica na tela de Relatórios (F9); aqui ela só aponta para o perfil.

**Testes**

- Combobox: digitar, selecionar, buscar sem acento, criar, fluxo de duplicidade (usar existente, continuar editando, reativar inativo) e teclado.
- Perfil: resumo e histórico paginado; edição com nome duplicado; inativar e reativar.
- Acessibilidade do combobox (papéis ARIA, foco) com jest-axe.

**Critério de aceite**

- É impossível criar cliente repetido pela interface e o usuário sempre entende como diferenciar ou reaproveitar.
- O perfil mostra os mesmos números que o back (B6) para o mesmo cliente.

#### F5 — Novo lançamento

**Objetivo.** Registrar um atendimento em poucos toques, com o cálculo visível antes de salvar.

**Requisitos:** RF21, RF22, RF23; RN01 a RN09, RN18, RN19. **Depende de:** F4 e B6.

**Tarefas**

1. Rota `/lancamentos/novo` (ação central da navegação) com o componente reutilizável `LancamentoForm`, que também serve à edição no F6.
2. Campos na ordem do uso: Cliente (`ClienteCombobox`), Serviço (combobox só com serviços ativos), Valor bruto (`MoneyInput`, preenchido com o `valorSugerido` do serviço quando o campo ainda está vazio), Percentual (numérico com sufixo %, `inputMode="decimal"`, de 0 a 100, até 2 casas), Data (padrão hoje, máximo hoje) e Observação (recolhida em “Adicionar observação”).
3. Descontos: seção “Houve desconto neste lançamento?”. Ao ativar, aparece uma lista dinâmica de linhas com Tipo (Select de tipos ativos, com “Criar tipo” inline), Modo (controle segmentado R$ ou %), Valor e Descrição opcional, com botões para adicionar e remover linha.
4. Prévia: cartão fixo no rodapé (celular) com Comissão, Descontos e Líquido, calculados por `lib/money` (`calcularComissao` e `calcularDesconto`, com `big.js` e `HALF_UP`) a cada alteração. Erro inline quando a soma dos descontos passa da comissão (espelha RN08). O valor oficial é sempre o da resposta da API.
5. Envio por `POST /lancamentos`, com o botão desabilitado enquanto envia. Sucesso: toast com o resumo (“Lançamento salvo: comissão R$ X”), formulário limpo mantendo a data, foco de volta no cliente e atalhos “Novo lançamento” e “Ver histórico”.
6. Erros por `code`: `CICLO_FECHADO` (mensagem “Reabra o ciclo para lançar” com link para o ciclo no F7), `DATA_FUTURA`, `SERVICO_INATIVO`, `CLIENTE_INATIVO`, `TIPO_DESCONTO_INATIVO` e `DESCONTO_EXCEDE_BASE`. Falha de rede preserva tudo o que foi digitado.
7. Rascunho em `sessionStorage` (sem tokens) para recuperar o formulário se o PWA for recarregado ao voltar do segundo plano; limpo ao salvar.
8. Acessibilidade: rótulos, erros ligados por `aria-describedby`, foco no primeiro campo inválido e alvos de toque grandes.

**Testes**

- Os vetores de `docs/test-vectors` (os mesmos do B6) rodam contra `lib/money`.
- Componente: a prévia atualiza ao digitar; desconto acima da comissão bloqueia o envio; valor sugerido do serviço; cada erro por `code` com MSW; recuperação do rascunho; atributos de teclado numérico; jest-axe.

**Critério de aceite**

- O lançamento é preenchido e salvo com uma mão, e o valor salvo é igual à prévia em todos os vetores.
- O erro de ciclo fechado explica o que fazer.

#### F6 — Histórico

**Objetivo.** Consultar, filtrar, editar e excluir lançamentos.

**Requisitos:** RF24, RF25; RN15, RN18. **Depende de:** F5 e B6.

**Tarefas**

1. `/historico` com barra de filtros: período (atalhos Hoje, Esta semana, Ciclo atual, Este mês e Personalizado), ciclo, serviço e cliente (combobox). Os filtros ficam na URL (`searchParams`), para o botão voltar e o compartilhamento funcionarem.
2. Lista com rolagem infinita (`useInfiniteQuery`, 20 por página), ordenada por data. Cada item mostra data, cliente, serviço, valor bruto, percentual, comissão, selo de desconto e líquido, e um cadeado quando o ciclo está fechado. Um cabeçalho fixo mostra o `resumo` do filtro (quantidade, comissão, descontos e líquido).
3. Ações por item: Editar (`/lancamentos/[id]/editar`, reutilizando `LancamentoForm` com `versao`) e Excluir (diálogo de confirmação). Em ciclo fechado as ações ficam desabilitadas com a explicação “Ciclo fechado: reabra para editar” e link para o ciclo.
4. Edição por `PUT` com `versao`; `CONFLITO_VERSAO` mostra “Este lançamento foi alterado em outro lugar” com botão para recarregar. Mudar a data para outro ciclo segue as regras do back.
5. Exclusão por `DELETE` com atualização otimista e reversão em caso de erro (não há desfazer, pois o back não restaura).
6. Estados: carregando (skeleton), vazio (com CTA “Novo lançamento”), sem resultado para o filtro (botão “Limpar filtros”) e erro com “Tentar de novo”.

**Testes**

- Filtros sincronizados com a URL; rolagem infinita; `resumo` do cabeçalho.
- Ações bloqueadas em ciclo fechado; edição com conflito de versão; exclusão com reversão em erro; estados vazios.

**Critério de aceite**

- O usuário encontra qualquer lançamento por período, cliente ou serviço e corrige erros de digitação em ciclo aberto.
- Em ciclo fechado, fica claro por que o lançamento está travado e como destravar.

#### F7 — Ciclos

**Objetivo.** Acompanhar ciclos, fechar com descontos do ciclo e reabrir.

**Requisitos:** RF11, RF12, RF13; RN07, RN14 a RN17. **Depende de:** F6 e B7.

**Tarefas**

1. `/ciclos`: lista com os mais recentes primeiro, mostrando período, selo Aberto ou Fechado, comissão, descontos e líquido; o ciclo atual em destaque; filtro por status; botão Fechar nos abertos e Reabrir nos fechados.
2. `/ciclos/[id]`: resumo (bruto, comissão, descontos dos lançamentos, descontos do ciclo e líquido), descontos do ciclo e lista dos lançamentos do período (componente do histórico, somente leitura quando fechado).
3. **Fechar** (Dialog ou Sheet em 3 etapas):
   - Etapa 1, resumo do período (bruto, comissão, descontos dos lançamentos e líquido parcial), com aviso quando a data final ainda não passou: “Este ciclo termina em dd/mm. Fechar agora impede novos lançamentos nele, inclusive nos dias que restam”.
   - Etapa 2, descontos do ciclo em lista dinâmica (tipo, R$ ou %, valor e descrição), com o % calculado sobre o líquido parcial e a prévia do líquido final (mesma lib de dinheiro do F5; erro quando a soma passa da base). Descontos que já existem (de uma reabertura) vêm pré-preenchidos.
   - Etapa 3, confirmação com as consequências (lançamentos travados, possibilidade de reabrir) e o botão “Fechar ciclo”.
4. **Reabrir:** diálogo explicando que os totais serão recalculados no próximo fechamento; `POST /ciclos/{id}/reabrir`; os lançamentos voltam a ser editáveis.
5. Erros: `CICLO_JA_FECHADO` e `CICLO_NAO_FECHADO` (recarregam o estado) e `DESCONTO_EXCEDE_BASE`.
6. Tela de sucesso do fechamento com o líquido a receber em destaque e link para o relatório de ciclos fechados (F9).

**Testes**

- Fluxo de 3 etapas com o cenário numérico do B7 (líquido de 124,69).
- Aviso de fechamento antecipado; pré-preenchimento após reabrir; bloqueio de desconto acima da base; reabertura; estados de lista vazia e de erro.

**Critério de aceite**

- O fechamento exibe exatamente os totais gravados pelo back.
- O usuário entende o efeito antes de confirmar.

#### F8 — Dashboard

**Objetivo.** Visão do ciclo atual, comparação, evolução e acumulado.

**Requisitos:** RF26, RF27, RF28. **Depende de:** F7 e B8.

**Tarefas**

1. `/dashboard` (rota inicial). Seção “Ciclo atual”: período, selo de status, **líquido** em destaque e, em linhas, comissão, descontos, quantidade de lançamentos, ticket médio e serviço mais rentável. Chip de comparação com o ciclo anterior (seta para cima ou para baixo com %; neutro e “—” quando a variação é nula).
2. Gráfico de evolução (últimos 6 ciclos, barras de comissão e linha do líquido) com Recharts carregado por `next/dynamic` (`ssr: false`), tooltip, cores seguras para daltonismo e **tabela alternativa** oculta visualmente (`sr-only`) para leitores de tela.
3. Card “Visão geral” com o acumulado: total de comissões (R$), total de lançamentos, clientes ativos, cliente que mais gerou comissão e ciclos fechados.
4. Ação principal “Novo lançamento” sempre visível e atalho para o histórico do ciclo atual.
5. Estados: skeleton, vazio (sem lançamentos, com um guia curto para registrar o primeiro) e erro com “Tentar de novo”. Formatação com `Intl` pt-BR via `lib/money` e `lib/datas`.

**Testes**

- Renderização com a massa de dados do B8, conferindo todos os campos.
- Variação positiva, negativa e nula; usuário sem dados; tabela alternativa do gráfico; formatação de moeda e datas.

**Critério de aceite**

- Os números são iguais aos do back e aos do histórico para o mesmo período.
- A tela carrega rápido no celular, dentro das metas de Lighthouse do F10.

#### F9 — Relatórios e exportação

**Objetivo.** Relatórios prontos, construtor personalizado, listagem de clientes e exportação fiel à tela.

**Requisitos:** RF19, RF29 a RF35. **Depende de:** F8 e B9 a B10.

**Tarefas**

1. `/relatorios` com três abas: **Prontos**, **Personalizado** e **Clientes**. Aba e filtros ficam na URL.
2. **Prontos:** galeria de cartões por grupo (Serviços, Clientes, Evolução, Descontos e Ciclos), cada um abrindo `/relatorios/[codigo]` com barra de filtros própria (período e parâmetros como “dias sem retorno” e “ordem”), gráfico quando aplicável, tabela e totais.
3. **Personalizado (construtor):** em uma tela, Filtros (período, ciclo, serviço e cliente, com seleção múltipla), Agrupar por, Métricas (caixas de seleção) e Ordenação. As regras da API valem na tela: “Tipo de desconto” só habilita com a métrica Descontos e períodos inválidos são bloqueados. Botão “Gerar”, parâmetros serializados na URL e uma nota fixa explicando como os descontos de ciclo entram (B9).
4. **Clientes:** listagem com nome, atendimentos, comissão gerada e última visita, com busca, ordenação por coluna e paginação; cada linha leva ao perfil (F4).
5. Renderização genérica: `RelatorioTabela` guiada por `colunas[].tipo` (moeda, inteiro, data, percentual e texto), com cabeçalho fixo, primeira coluna fixa e rolagem horizontal no celular e linha de totais; `RelatorioGrafico` escolhe linha ou barras no tempo para `DIA`, `SEMANA`, `MES` e `CICLO`, e barras horizontais do top 10 nos demais casos. Aviso quando `truncado` for verdadeiro (“Mostrando as primeiras N linhas; refine os filtros”).
6. **Exportação:** “Exportar PDF” e “Exportar CSV” reutilizam os **mesmos parâmetros da URL** que geraram a tela, chamam o endpoint de exportação e disparam o download com o nome vindo de `Content-Disposition`; estados de carregamento e de erro (`RELATORIO_MUITO_GRANDE` orienta a filtrar). No iOS, o PDF abre em nova aba como alternativa.
7. Estados vazios por relatório (“Sem dados no período”) e erro `RELATORIO_INVALIDO` mapeado.

**Testes**

- Matriz de validação do construtor (combinações permitidas e bloqueadas); renderização de cada tipo de coluna; aviso de `truncado`.
- Paridade: os parâmetros enviados na exportação são idênticos aos da consulta exibida; download simulado com MSW.
- Listagem de clientes (busca e ordenação); acessibilidade das tabelas.

**Critério de aceite**

- Cada relatório pronto abre com dados corretos e o construtor gera qualquer combinação válida.
- Os arquivos exportados refletem exatamente o que está na tela.

#### F10 — Acabamento e qualidade final

**Objetivo.** Garantir qualidade ponta a ponta antes do uso real.

**Requisitos:** RNF de desempenho, acessibilidade, segurança e usabilidade. **Depende de:** F9 e B11.

**Tarefas**

1. **E2E com Playwright** contra o ambiente do Docker Compose, lendo o código de verificação pela API do Mailpit: (a) cadastro, código, primeiro acesso e dashboard; (b) serviço e cliente, lançamento com desconto e histórico; (c) fechar ciclo com desconto de ciclo, lançamento bloqueado, reabrir e editar; (d) relatório personalizado e exportação em CSV e PDF; (e) login por nome de usuário e recuperação de senha; (f) isolamento entre dois usuários; (g) tentativa de cliente duplicado.
2. **Desempenho:** Lighthouse CI no celular, com metas de partida de 90 ou mais em desempenho, acessibilidade, boas práticas e PWA; `@next/bundle-analyzer` com orçamento por rota; imagens e fontes otimizadas.
3. **Acessibilidade:** axe automatizado nos E2E e passada manual (teclado, leitor de tela básico, zoom de 200%, `prefers-reduced-motion` e contraste).
4. **Segurança:** conferir CSP (inclui o domínio do Google), headers e ausência de tokens em armazenamento; sem `dangerouslySetInnerHTML`; dependências auditadas (`pnpm audit` e Dependabot).
5. **Revisão de UX e textos:** estados vazios, de carregamento e de erro de todas as telas; mensagens em pt-BR coerentes com o catálogo de erros; páginas 404 e 500.
6. **Compatibilidade:** iOS Safari (PWA instalado), Android Chrome e, no desktop, Chrome, Firefox e Safari.
7. **Release:** tag de versão, changelog, README final e dados de demonstração (`seed`).

**Testes**

- As próprias suítes E2E e auditorias acima: os fluxos (a) e (b) rodam em todo pull request, e a suíte completa antes de cada release.

**Critério de aceite**

- Todos os E2E passam, as metas de Lighthouse são atingidas e não há violação crítica de acessibilidade.
- O app instalado funciona em iOS e Android.

## 4. Ordem de entrega

As fases de back e front andam juntas em sete fatias; a **fatia 3** já entrega o núcleo útil do produto.

| Fatia | Back-end | Front-end | Resultado utilizável |
| --- | --- | --- | --- |
| 1. Autenticação | B0 a B3 | F0 a F2 | Criar conta, verificar e entrar (e-mail, usuário ou Google). |
| 2. Cadastros | B4 | F3, F4 | Cadastrar serviços, clientes e tipos de desconto. |
| 3. Lançar | B5, B6 | F5, F6 | Registrar e consultar lançamentos com comissão e descontos. |
| 4. Fechar ciclo | B7 | F7 | Fechar e reabrir ciclos com descontos do ciclo. |
| 5. Visão geral | B8 | F8 | Dashboard e card acumulado. |
| 6. Relatórios | B9, B10 | F9 | Relatórios, construtor e exportação. |
| 7. Entrega | B11 | F10 | Homologação, ajustes finais e uso real. |

Recomendação: colocar a primeira usuária para testar ao fim da fatia 3, para validar o fluxo de lançamento com uso real antes de construir o restante.

## 5. Qualidade, CI/CD e ambientes

- **CI (GitHub Actions):** back com build, testes (Testcontainers), Spotless e JaCoCo; front com lint, `tsc`, Vitest e build; E2E em job separado.
- **Pirâmide de testes:** muitos unitários no domínio (cálculo, ciclo, normalização), integração com Testcontainers (repositórios, segurança, isolamento) e poucos E2E nos fluxos críticos.
- **Ambientes:** local (Docker Compose), homologação e produção; variáveis por ambiente; migrations aplicadas automaticamente no deploy.
- **Definição de pronto:** código revisado, testes passando, regras de negócio cobertas, OpenAPI atualizado, nenhum segredo no repositório, migration revisada e documentação atualizada.

## 6. Pontos de atenção

- **Fuso horário:** “hoje” e a validação de data futura usam fuso fixo configurável (padrão `America/Recife`); fuso por usuário fica como melhoria futura.
- **Fechamento versus lançamento simultâneos:** tratado com locks no ciclo (B7) e coberto por teste de concorrência.
- **Provedor de e-mail:** a abstração `EmailSender` permite trocar de provedor; custo e limites de envio entram na escolha.
- **Login com Google:** configurar a tela de consentimento OAuth no Google Cloud antes do uso real.
- **Dados pessoais (LGPD):** o sistema guarda nome e telefone de clientes finais; avaliar política de privacidade e exclusão de conta e dados antes de abrir para usuários reais, pois isso ainda não está nos requisitos.
- **PDF:** layout simples no MVP; refinamento visual fica para depois.

## 7. Contratos compartilhados entre back e front

O que back e front precisam combinar uma única vez: códigos de erro, vetores de teste e convenções de formato.

### 7.1 Catálogo de códigos de erro

| Código | HTTP | Quando acontece | Mensagem sugerida |
| --- | --- | --- | --- |
| `VALIDACAO` | 400 | Campo inválido (vem com `fields[]`) | Mensagem do campo |
| `NAO_AUTENTICADO` | 401 | Sem token ou token expirado | Sua sessão expirou. Entre novamente. |
| `CREDENCIAIS_INVALIDAS` | 401 | Login com identificador ou senha incorretos | E-mail, usuário ou senha incorretos. |
| `SESSAO_INVALIDA` | 401 | Refresh inválido, expirado ou reutilizado | Sua sessão foi encerrada. Entre novamente. |
| `TOKEN_GOOGLE_INVALIDO` | 401 | Token do Google recusado | Não foi possível entrar com o Google. |
| `EMAIL_NAO_VERIFICADO` | 403 | Login com senha correta e e-mail não verificado | Confirme seu e-mail para continuar. |
| `RECURSO_NAO_ENCONTRADO` | 404 | Recurso inexistente ou de outro usuário | Não encontramos este item. |
| `USERNAME_EM_USO` | 409 | Nome de usuário já existe | Este nome de usuário já está em uso. |
| `EMAIL_EM_USO` | 409 | E-mail já cadastrado | Já existe um cadastro com este e-mail. |
| `CODIGO_INVALIDO` | 400 | Código de verificação incorreto | Código incorreto. |
| `CODIGO_EXPIRADO` | 400 | Código vencido | Este código expirou. Peça um novo. |
| `TENTATIVAS_EXCEDIDAS` | 400 | Muitas tentativas no mesmo código | Muitas tentativas. Peça um novo código. |
| `LIMITE_EXCEDIDO` | 429 | Limite de requisições (traz `Retry-After`) | Muitas tentativas. Tente de novo em instantes. |
| `CLIENTE_DUPLICADO` | 409 | Nome normalizado já existe (traz `clienteExistente`) | Já existe um cliente com este nome. Selecione-o ou diferencie o nome. |
| `SERVICO_INATIVO` | 422 | Lançamento com serviço inativo | Este serviço está inativo. |
| `CLIENTE_INATIVO` | 422 | Lançamento com cliente inativo | Este cliente está inativo. |
| `TIPO_DESCONTO_INATIVO` | 422 | Desconto com tipo inativo | Este tipo de desconto está inativo. |
| `DATA_FUTURA` | 422 | Data do lançamento depois de hoje | A data não pode ser futura. |
| `CICLO_FECHADO` | 409 | Criar, editar ou excluir em ciclo fechado (traz `cicloId`) | Este ciclo está fechado. Reabra para lançar. |
| `CICLO_JA_FECHADO` | 409 | Fechar um ciclo já fechado | Este ciclo já está fechado. |
| `CICLO_NAO_FECHADO` | 409 | Reabrir um ciclo aberto | Este ciclo já está aberto. |
| `DESCONTO_EXCEDE_BASE` | 422 | Soma dos descontos maior que a base | Os descontos não podem passar do valor da comissão. |
| `CONFLITO_VERSAO` | 409 | Edição com `versao` desatualizada | Este item foi alterado em outro lugar. Recarregue. |
| `RELATORIO_INVALIDO` | 400 | Combinação inválida de filtro, agrupamento ou métrica | Esta combinação de relatório não é válida. |
| `RELATORIO_MUITO_GRANDE` | 422 | Exportação acima do limite de linhas | Resultado muito grande. Refine os filtros. |

### 7.2 Vetores de teste compartilhados

Arquivos JSON em `docs/test-vectors`, lidos pelos testes dos dois lados. Alterar um vetor exige o CI verde em ambos.

| Arquivo | Conteúdo | Consumidores |
| --- | --- | --- |
| `comissao.json` | Valor bruto e percentual com a comissão esperada (inclui arredondamento) | B6, F5 |
| `desconto.json` | Comissão, modo e valor com o desconto esperado; casos de excedente | B6, B7, F5, F7 |
| `fechamento.json` | Cenário numérico do B7: lançamentos, descontos e totais esperados | B7, F7 |
| `ciclos.json` | Configuração e data com o período esperado (casos do B5) | B5 e mocks do F2 |

### 7.3 Convenções de formato

- **Dinheiro e percentuais:** o back serializa `BigDecimal` como string decimal com ponto (`"1234.50"`), nunca como número JSON; o front só converte para exibir.
- **Datas:** `YYYY-MM-DD`, sem horário; instantes em ISO-8601 UTC.
- **Identificadores:** UUID em string. **Nomes de campos:** `camelCase` em português. **Enums:** `MAIUSCULAS_COM_UNDERLINE`.
- **Paginação:** `{conteudo, pagina, tamanho, totalElementos, totalPaginas}`, com `size` máximo definido por endpoint.
- **Erros:** sempre `ProblemDetail` com `code`; o front decide a mensagem pelo `code`, não pelo texto do back.
- **Versionamento:** prefixo `/api/v1`; campos novos são aditivos e mudança incompatível vira `/api/v2`.
- **Autenticação:** `Authorization: Bearer` nas chamadas e cookies `refresh_token` e `sessao_ativa` definidos pelo back (B3).
