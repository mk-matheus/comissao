package com.mkmateus.api.modelo;

import com.mkmateus.api.auth.CodigoVerificacao;
import com.mkmateus.api.auth.CodigoVerificacaoRepository;
import com.mkmateus.api.auth.RefreshToken;
import com.mkmateus.api.auth.RefreshTokenRepository;
import com.mkmateus.api.auth.TipoCodigoVerificacao;
import com.mkmateus.api.ciclo.Ciclo;
import com.mkmateus.api.ciclo.CicloRepository;
import com.mkmateus.api.ciclo.ConfiguracaoCiclo;
import com.mkmateus.api.ciclo.ConfiguracaoCicloRepository;
import com.mkmateus.api.ciclo.ContagemCiclo;
import com.mkmateus.api.ciclo.StatusCiclo;
import com.mkmateus.api.ciclo.TipoCiclo;
import com.mkmateus.api.cliente.Cliente;
import com.mkmateus.api.cliente.ClienteRepository;
import com.mkmateus.api.common.jpa.RecursoUsuarioRepository;
import com.mkmateus.api.desconto.Desconto;
import com.mkmateus.api.desconto.DescontoRepository;
import com.mkmateus.api.desconto.ModoDesconto;
import com.mkmateus.api.desconto.TipoDesconto;
import com.mkmateus.api.desconto.TipoDescontoRepository;
import com.mkmateus.api.lancamento.Lancamento;
import com.mkmateus.api.lancamento.LancamentoRepository;
import com.mkmateus.api.servico.Servico;
import com.mkmateus.api.servico.ServicoRepository;
import com.mkmateus.api.support.AbstractIntegrationTest;
import com.mkmateus.api.usuario.Usuario;
import com.mkmateus.api.usuario.UsuarioRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModeloDadosIntegrationTest extends AbstractIntegrationTest {

    private static final String HASH_64 = "a".repeat(64);

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Environment environment;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private CodigoVerificacaoRepository codigoVerificacaoRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private ConfiguracaoCicloRepository configuracaoCicloRepository;

    @Autowired
    private CicloRepository cicloRepository;

    @Autowired
    private ServicoRepository servicoRepository;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private TipoDescontoRepository tipoDescontoRepository;

    @Autowired
    private LancamentoRepository lancamentoRepository;

    @Autowired
    private DescontoRepository descontoRepository;

    @AfterEach
    void limparTabelas() {
        jdbc.execute("""
                TRUNCATE TABLE
                    desconto,
                    lancamento,
                    tipo_desconto,
                    cliente,
                    servico,
                    ciclo,
                    configuracao_ciclo,
                    refresh_token,
                    codigo_verificacao,
                    usuario
                CASCADE
                """);
    }

    @Test
    void flywayAplicaTodasAsMigrationsESchemaEhValidadoSemDdlAutoGerandoTabelas() {
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");

        Integer migracoesSucesso = jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = true",
                Integer.class);
        assertThat(migracoesSucesso).isEqualTo(6);

        Boolean btreeGistAtivo = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'btree_gist')",
                Boolean.class);
        assertThat(btreeGistAtivo).isTrue();

        List<String> indicesV6 = jdbc.queryForList("""
                SELECT indexname FROM pg_indexes
                WHERE schemaname = 'public'
                  AND indexname IN (
                      'idx_lancamento_usuario_data_ativo',
                      'idx_lancamento_usuario_cliente',
                      'idx_lancamento_usuario_ciclo',
                      'idx_lancamento_usuario_servico',
                      'idx_desconto_lancamento',
                      'idx_desconto_ciclo'
                  )
                """, String.class);
        assertThat(indicesV6).hasSize(6);
    }

    @Test
    void rejeitaUsernameComMaiusculaNoBanco() {
        assertThatThrownBy(() -> inserirUsuarioSql("João", "Joao.Silva", "joao@teste.com", "hash"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause()
                .hasMessageContaining("ck_usuario_username_formato");
    }

    @Test
    void rejeitaEmailDuplicadoECredencialVaziaNoBanco() {
        inserirUsuarioSql("Maria", "maria.um", "maria@teste.com", "hash");

        assertThatThrownBy(() -> inserirUsuarioSql("Maria 2", "maria.dois", "maria@teste.com", "hash"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause()
                .hasMessageContaining("uk_usuario_email");

        assertThatThrownBy(() -> inserirUsuarioSql("Maria 3", "maria.tres", "MARIA@teste.com", "hash"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause()
                .hasMessageContaining("uk_usuario_email_lower");

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO usuario (id, nome, username, email, senha_hash, google_id)
                VALUES (?, 'Sem Credencial', 'sem.credencial', 'semcred@teste.com', NULL, NULL)
                """, UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause()
                .hasMessageContaining("ck_usuario_credencial");
    }

    @Test
    void rejeitaPercentualAcimaDe100EValorBrutoZeroOuServicoValorSugeridoZeroNoBanco() {
        FixturesSql f = criarFixturesSql("ana.lanc", "ana.lanc@teste.com");

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO lancamento (
                    id, usuario_id, ciclo_id, servico_id, servico_nome, cliente_id,
                    data, valor_bruto, percentual, valor_comissao
                ) VALUES (?, ?, ?, ?, 'Corte', ?, ?, 100.00, 100.01, 100.01)
                """, UUID.randomUUID(), f.usuarioId(), f.cicloId(), f.servicoId(), f.clienteId(),
                Date.valueOf("2026-11-05")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause()
                .hasMessageContaining("ck_lancamento_percentual");

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO lancamento (
                    id, usuario_id, ciclo_id, servico_id, servico_nome, cliente_id,
                    data, valor_bruto, percentual, valor_comissao
                ) VALUES (?, ?, ?, ?, 'Corte', ?, ?, 0.00, 40.00, 0.00)
                """, UUID.randomUUID(), f.usuarioId(), f.cicloId(), f.servicoId(), f.clienteId(),
                Date.valueOf("2026-11-05")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause()
                .hasMessageContaining("ck_lancamento_valor_bruto");

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO servico (id, usuario_id, nome, valor_sugerido)
                VALUES (?, ?, 'Serviço Zerado', 0.00)
                """, UUID.randomUUID(), f.usuarioId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause()
                .hasMessageContaining("ck_servico_valor_sugerido");
    }

    @Test
    void rejeitaDescontoSemAlvoComDoisAlvosOuPercentualAcimaDe100NoBanco() {
        FixturesSql f = criarFixturesSql("bia.desc", "bia.desc@teste.com");
        UUID lancamentoId = inserirLancamentoSql(f);

        // Sem alvo (lancamento_id e ciclo_id nulos)
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO desconto (
                    id, usuario_id, tipo_desconto_id, lancamento_id, ciclo_id,
                    modo, valor_informado, valor_calculado
                ) VALUES (?, ?, ?, NULL, NULL, 'VALOR', 10.00, 10.00)
                """, UUID.randomUUID(), f.usuarioId(), f.tipoDescontoId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause()
                .hasMessageContaining("ck_desconto_alvo_unico");

        // Com os dois alvos preenchidos simultaneamente
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO desconto (
                    id, usuario_id, tipo_desconto_id, lancamento_id, ciclo_id,
                    modo, valor_informado, valor_calculado
                ) VALUES (?, ?, ?, ?, ?, 'VALOR', 10.00, 10.00)
                """, UUID.randomUUID(), f.usuarioId(), f.tipoDescontoId(), lancamentoId, f.cicloId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause()
                .hasMessageContaining("ck_desconto_alvo_unico");

        // Percentual > 100 no desconto
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO desconto (
                    id, usuario_id, tipo_desconto_id, lancamento_id, ciclo_id,
                    modo, valor_informado, valor_calculado
                ) VALUES (?, ?, ?, ?, NULL, 'PERCENTUAL', 100.01, 10.00)
                """, UUID.randomUUID(), f.usuarioId(), f.tipoDescontoId(), lancamentoId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause()
                .hasMessageContaining("ck_desconto_percentual_maximo");
    }

    @Test
    void rejeitaNomeNormalizadoRepetidoNoMesmoUsuarioInclusiveInativoEAceitaEmUsuariosDiferentes() {
        UUID usuarioA = inserirUsuarioSql("Usuária A", "usuaria.a", "a@teste.com", "hash");
        UUID usuarioB = inserirUsuarioSql("Usuária B", "usuaria.b", "b@teste.com", "hash");

        jdbc.update("""
                INSERT INTO cliente (id, usuario_id, nome, nome_normalizado, ativo)
                VALUES (?, ?, 'Maria das Graças', 'maria das gracas', FALSE)
                """, UUID.randomUUID(), usuarioA);

        // Mesmo usuário, mesmo nome normalizado (mesmo estando inativo) deve falhar
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO cliente (id, usuario_id, nome, nome_normalizado, ativo)
                VALUES (?, ?, 'maria das gracas', 'maria das gracas', TRUE)
                """, UUID.randomUUID(), usuarioA))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause()
                .hasMessageContaining("uk_cliente_usuario_nome_normalizado");

        // Outro usuário com o mesmo nome normalizado é aceito
        int inseridosOutroUsuario = jdbc.update("""
                INSERT INTO cliente (id, usuario_id, nome, nome_normalizado, ativo)
                VALUES (?, ?, 'Maria das Graças', 'maria das gracas', TRUE)
                """, UUID.randomUUID(), usuarioB);
        assertThat(inseridosOutroUsuario).isEqualTo(1);
    }

    @Test
    void rejeitaCiclosSobrepostosDatasInvertidasConfiguracaoSemAncoraEAceitaCiclosAdjacentesNoBanco() {
        UUID usuarioId = inserirUsuarioSql("Clara", "clara.ciclo", "clara@teste.com", "hash");
        UUID outroUsuarioId = inserirUsuarioSql("Duda", "duda.ciclo", "duda@teste.com", "hash");

        // Configuração A_PARTIR_DE_DATA sem data_inicial deve falhar
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO configuracao_ciclo (id, usuario_id, tipo, contagem, data_inicial, vigente_a_partir_de)
                VALUES (?, ?, 'QUINZENAL', 'A_PARTIR_DE_DATA', NULL, ?)
                """, UUID.randomUUID(), usuarioId, Date.valueOf("2026-11-01")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause()
                .hasMessageContaining("ck_configuracao_ciclo_data_inicial");

        // data_fim < data_inicio deve falhar
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO ciclo (id, usuario_id, data_inicio, data_fim)
                VALUES (?, ?, ?, ?)
                """, UUID.randomUUID(), usuarioId, Date.valueOf("2026-11-15"), Date.valueOf("2026-11-01")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause()
                .hasMessageContaining("ck_ciclo_datas");

        jdbc.update("""
                INSERT INTO ciclo (id, usuario_id, data_inicio, data_fim)
                VALUES (?, ?, ?, ?)
                """, UUID.randomUUID(), usuarioId, Date.valueOf("2026-11-01"), Date.valueOf("2026-11-15"));

        // Sobreposição parcial (começa no dia 15, quando o anterior ainda está vigente)
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO ciclo (id, usuario_id, data_inicio, data_fim)
                VALUES (?, ?, ?, ?)
                """, UUID.randomUUID(), usuarioId, Date.valueOf("2026-11-15"), Date.valueOf("2026-11-30")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause()
                .hasMessageContaining("ex_ciclo_sem_sobreposicao");

        // Sobreposição interna
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO ciclo (id, usuario_id, data_inicio, data_fim)
                VALUES (?, ?, ?, ?)
                """, UUID.randomUUID(), usuarioId, Date.valueOf("2026-11-05"), Date.valueOf("2026-11-10")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause()
                .hasMessageContaining("ex_ciclo_sem_sobreposicao");

        // Adjacente (termina dia 15, próximo começa dia 16) é aceito
        int adjacente = jdbc.update("""
                INSERT INTO ciclo (id, usuario_id, data_inicio, data_fim)
                VALUES (?, ?, ?, ?)
                """, UUID.randomUUID(), usuarioId, Date.valueOf("2026-11-16"), Date.valueOf("2026-11-30"));
        assertThat(adjacente).isEqualTo(1);

        // Mesmo período em outro usuário é aceito
        int outroUsuario = jdbc.update("""
                INSERT INTO ciclo (id, usuario_id, data_inicio, data_fim)
                VALUES (?, ?, ?, ?)
                """, UUID.randomUUID(), outroUsuarioId, Date.valueOf("2026-11-01"), Date.valueOf("2026-11-15"));
        assertThat(outroUsuario).isEqualTo(1);
    }

    @Test
    void rejeitaReferenciaARegistroDeOutroUsuarioPorChaveEstrangeiraComposta() {
        FixturesSql usuarioA = criarFixturesSql("user.alfa", "alfa@teste.com");
        FixturesSql usuarioB = criarFixturesSql("user.beta", "beta@teste.com");
        UUID lancamentoA = inserirLancamentoSql(usuarioA);

        // Lançamento do usuário A apontando para cliente do usuário B
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO lancamento (
                    id, usuario_id, ciclo_id, servico_id, servico_nome, cliente_id,
                    data, valor_bruto, percentual, valor_comissao
                ) VALUES (?, ?, ?, ?, 'Corte', ?, ?, 100.00, 50.00, 50.00)
                """, UUID.randomUUID(), usuarioA.usuarioId(), usuarioA.cicloId(),
                usuarioA.servicoId(), usuarioB.clienteId(), Date.valueOf("2026-11-05")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause()
                .hasMessageContaining("fk_lancamento_cliente");

        // Lançamento do usuário A apontando para serviço do usuário B
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO lancamento (
                    id, usuario_id, ciclo_id, servico_id, servico_nome, cliente_id,
                    data, valor_bruto, percentual, valor_comissao
                ) VALUES (?, ?, ?, ?, 'Corte', ?, ?, 100.00, 50.00, 50.00)
                """, UUID.randomUUID(), usuarioA.usuarioId(), usuarioA.cicloId(),
                usuarioB.servicoId(), usuarioA.clienteId(), Date.valueOf("2026-11-05")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause()
                .hasMessageContaining("fk_lancamento_servico");

        // Lançamento do usuário A apontando para ciclo do usuário B
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO lancamento (
                    id, usuario_id, ciclo_id, servico_id, servico_nome, cliente_id,
                    data, valor_bruto, percentual, valor_comissao
                ) VALUES (?, ?, ?, ?, 'Corte', ?, ?, 100.00, 50.00, 50.00)
                """, UUID.randomUUID(), usuarioA.usuarioId(), usuarioB.cicloId(),
                usuarioA.servicoId(), usuarioA.clienteId(), Date.valueOf("2026-11-05")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause()
                .hasMessageContaining("fk_lancamento_ciclo");

        // Desconto do usuário B apontando para lançamento do usuário A
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO desconto (
                    id, usuario_id, tipo_desconto_id, lancamento_id, ciclo_id,
                    modo, valor_informado, valor_calculado
                ) VALUES (?, ?, ?, ?, NULL, 'VALOR', 5.00, 5.00)
                """, UUID.randomUUID(), usuarioB.usuarioId(), usuarioB.tipoDescontoId(), lancamentoA))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause()
                .hasMessageContaining("fk_desconto_lancamento");

        // Desconto do usuário A apontando para tipo_desconto do usuário B
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO desconto (
                    id, usuario_id, tipo_desconto_id, lancamento_id, ciclo_id,
                    modo, valor_informado, valor_calculado
                ) VALUES (?, ?, ?, ?, NULL, 'VALOR', 5.00, 5.00)
                """, UUID.randomUUID(), usuarioA.usuarioId(), usuarioB.tipoDescontoId(), lancamentoA))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause()
                .hasMessageContaining("fk_desconto_tipo_desconto");

        // Desconto do usuário B apontando para ciclo do usuário A
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO desconto (
                    id, usuario_id, tipo_desconto_id, lancamento_id, ciclo_id,
                    modo, valor_informado, valor_calculado
                ) VALUES (?, ?, ?, NULL, ?, 'VALOR', 5.00, 5.00)
                """, UUID.randomUUID(), usuarioB.usuarioId(), usuarioB.tipoDescontoId(), usuarioA.cicloId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause()
                .hasMessageContaining("fk_desconto_ciclo");

        // Refresh token do usuário B apontando substituido_por para token do usuário A
        UUID tokenA = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO refresh_token (id, usuario_id, familia_id, token_hash, expira_em)
                VALUES (?, ?, ?, ?, NOW() + INTERVAL '1 day')
                """, tokenA, usuarioA.usuarioId(), UUID.randomUUID(), "c".repeat(64));

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO refresh_token (id, usuario_id, familia_id, token_hash, expira_em, substituido_por)
                VALUES (?, ?, ?, ?, NOW() + INTERVAL '1 day', ?)
                """, UUID.randomUUID(), usuarioB.usuarioId(), UUID.randomUUID(), "d".repeat(64), tokenA))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause()
                .hasMessageContaining("fk_refresh_token_substituido_por");
    }

    @Test
    @Transactional
    void mapeamentoJpaAuditingVersaoLazyEIsolamentoNosRepositoriosFuncionam() {
        Usuario dona = usuarioRepository.saveAndFlush(
                new Usuario("Fernanda", "fernanda.jpa", "fernanda@teste.com", "hash-bcrypt", null));
        Usuario intrusa = usuarioRepository.saveAndFlush(
                new Usuario("Intrusa", "intrusa.jpa", "intrusa@teste.com", "hash-bcrypt", null));

        assertThat(dona.getId()).isNotNull();
        assertThat(dona.getCriadoEm()).isNotNull();
        assertThat(dona.getAtualizadoEm()).isNotNull();

        CodigoVerificacao codigo = codigoVerificacaoRepository.saveAndFlush(
                new CodigoVerificacao(dona, TipoCodigoVerificacao.VERIFICACAO_EMAIL, HASH_64,
                        Instant.now().plusSeconds(900)));
        RefreshToken refresh = refreshTokenRepository.saveAndFlush(
                new RefreshToken(dona, UUID.randomUUID(), "b".repeat(64), Instant.now().plusSeconds(86400)));
        ConfiguracaoCiclo cfg = configuracaoCicloRepository.saveAndFlush(
                new ConfiguracaoCiclo(dona, TipoCiclo.QUINZENAL, ContagemCiclo.CALENDARIO,
                        null, LocalDate.of(2026, 11, 1)));
        Ciclo ciclo = cicloRepository.saveAndFlush(
                new Ciclo(dona, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 15)));
        Servico servico = servicoRepository.saveAndFlush(
                new Servico(dona, "Escova", new BigDecimal("80.00")));
        Cliente cliente = clienteRepository.saveAndFlush(
                new Cliente(dona, "Patrícia Lima", "patricia lima", "(81) 99999-0000", null));
        TipoDesconto tipoDesconto = tipoDescontoRepository.saveAndFlush(
                new TipoDesconto(dona, "Taxa de maquininha"));
        Lancamento lancamento = lancamentoRepository.saveAndFlush(
                new Lancamento(dona, ciclo, servico, servico.getNome(), cliente,
                        LocalDate.of(2026, 11, 6),
                        new BigDecimal("80.00"), new BigDecimal("50.00"), new BigDecimal("40.00"),
                        "Atendimento pontual"));
        Desconto descontoLancamento = descontoRepository.saveAndFlush(
                Desconto.paraLancamento(dona, tipoDesconto, lancamento, ModoDesconto.PERCENTUAL,
                        new BigDecimal("10.00"), new BigDecimal("4.00"), "Cartão crédito"));
        Desconto descontoCiclo = descontoRepository.saveAndFlush(
                Desconto.paraCiclo(dona, tipoDesconto, ciclo, ModoDesconto.VALOR,
                        new BigDecimal("15.00"), new BigDecimal("15.00"), "Adiantamento"));

        assertThat(ciclo.getVersao()).isZero();
        assertThat(lancamento.getVersao()).isZero();

        ciclo.setStatus(StatusCiclo.FECHADO);
        cicloRepository.saveAndFlush(ciclo);
        lancamento.setObservacao("Observação atualizada");
        lancamentoRepository.saveAndFlush(lancamento);

        assertThat(ciclo.getVersao()).isEqualTo(1L);
        assertThat(lancamento.getVersao()).isEqualTo(1L);

        entityManager.clear();

        Lancamento recarregado = lancamentoRepository.findByIdAndUsuarioId(lancamento.getId(), dona.getId())
                .orElseThrow();
        assertThat(Hibernate.isInitialized(recarregado.getUsuario())).isFalse();
        assertThat(Hibernate.isInitialized(recarregado.getCiclo())).isFalse();
        assertThat(Hibernate.isInitialized(recarregado.getServico())).isFalse();
        assertThat(Hibernate.isInitialized(recarregado.getCliente())).isFalse();

        // Isolamento: outro usuário nunca encontra os recursos da dona
        assertThat(codigoVerificacaoRepository.findByIdAndUsuarioId(codigo.getId(), intrusa.getId())).isEmpty();
        assertThat(refreshTokenRepository.findByIdAndUsuarioId(refresh.getId(), intrusa.getId())).isEmpty();
        assertThat(configuracaoCicloRepository.findByIdAndUsuarioId(cfg.getId(), intrusa.getId())).isEmpty();
        assertThat(cicloRepository.findByIdAndUsuarioId(ciclo.getId(), intrusa.getId())).isEmpty();
        assertThat(servicoRepository.findByIdAndUsuarioId(servico.getId(), intrusa.getId())).isEmpty();
        assertThat(clienteRepository.findByIdAndUsuarioId(cliente.getId(), intrusa.getId())).isEmpty();
        assertThat(tipoDescontoRepository.findByIdAndUsuarioId(tipoDesconto.getId(), intrusa.getId())).isEmpty();
        assertThat(lancamentoRepository.findByIdAndUsuarioId(lancamento.getId(), intrusa.getId())).isEmpty();
        assertThat(descontoRepository.findByIdAndUsuarioId(descontoLancamento.getId(), intrusa.getId())).isEmpty();
        assertThat(descontoRepository.findByIdAndUsuarioId(descontoCiclo.getId(), intrusa.getId())).isEmpty();
    }

    @Test
    void repositoriosDeRecursoDeUsuarioNaoExpoemFindByIdNemFindAllPuros() {
        List<Class<? extends RecursoUsuarioRepository<?>>> repositorios = List.of(
                CodigoVerificacaoRepository.class,
                RefreshTokenRepository.class,
                ConfiguracaoCicloRepository.class,
                CicloRepository.class,
                ServicoRepository.class,
                ClienteRepository.class,
                TipoDescontoRepository.class,
                LancamentoRepository.class,
                DescontoRepository.class);

        for (Class<?> repo : repositorios) {
            List<String> nomesMetodos = Arrays.stream(repo.getMethods())
                    .map(Method::getName)
                    .toList();
            assertThat(nomesMetodos)
                    .as("Repositório %s não deve expor métodos sem filtro por usuário", repo.getSimpleName())
                    .doesNotContain("findById", "deleteById", "findAll", "deleteAll");
        }
    }

    private UUID inserirUsuarioSql(String nome, String username, String email, String senhaHash) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO usuario (id, nome, username, email, senha_hash)
                VALUES (?, ?, ?, ?, ?)
                """, id, nome, username, email, senhaHash);
        return id;
    }

    private FixturesSql criarFixturesSql(String username, String email) {
        UUID usuarioId = inserirUsuarioSql("Usuária " + username, username, email, "hash");
        UUID cicloId = UUID.randomUUID();
        UUID servicoId = UUID.randomUUID();
        UUID clienteId = UUID.randomUUID();
        UUID tipoDescontoId = UUID.randomUUID();

        jdbc.update("""
                INSERT INTO ciclo (id, usuario_id, data_inicio, data_fim)
                VALUES (?, ?, ?, ?)
                """, cicloId, usuarioId, Date.valueOf("2026-11-01"), Date.valueOf("2026-11-15"));

        jdbc.update("""
                INSERT INTO servico (id, usuario_id, nome, valor_sugerido)
                VALUES (?, ?, 'Corte', 100.00)
                """, servicoId, usuarioId);

        jdbc.update("""
                INSERT INTO cliente (id, usuario_id, nome, nome_normalizado)
                VALUES (?, ?, 'Cliente Base', 'cliente base')
                """, clienteId, usuarioId);

        jdbc.update("""
                INSERT INTO tipo_desconto (id, usuario_id, nome)
                VALUES (?, ?, 'Taxa')
                """, tipoDescontoId, usuarioId);

        return new FixturesSql(usuarioId, cicloId, servicoId, clienteId, tipoDescontoId);
    }

    private UUID inserirLancamentoSql(FixturesSql f) {
        UUID lancamentoId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO lancamento (
                    id, usuario_id, ciclo_id, servico_id, servico_nome, cliente_id,
                    data, valor_bruto, percentual, valor_comissao
                ) VALUES (?, ?, ?, ?, 'Corte', ?, ?, 100.00, 40.00, 40.00)
                """, lancamentoId, f.usuarioId(), f.cicloId(), f.servicoId(), f.clienteId(),
                Date.valueOf("2026-11-05"));
        return lancamentoId;
    }

    private record FixturesSql(
            UUID usuarioId,
            UUID cicloId,
            UUID servicoId,
            UUID clienteId,
            UUID tipoDescontoId) {
    }
}
