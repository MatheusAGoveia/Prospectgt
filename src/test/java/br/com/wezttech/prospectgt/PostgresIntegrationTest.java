package br.com.wezttech.prospectgt;

import static org.assertj.core.api.Assertions.*;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.*;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Repete os fluxos HTTP e a disputa de reserva no PostgreSQL real. */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PostgresIntegrationTest extends FluxoIntegrationTest {

  @Test
  void migracaoEntreH2EPostgresPreservaCarteiraESenhasSemSobrescreverDestino() throws Exception {
    reservas.assumir(lead.id, a);
    reservas.registrar(
      lead.id,
      a,
      "FOLLOW_UP",
      "Feedback a preservar",
      java.time.LocalDate.now().plusDays(2)
    );
    jobs.criar("ANALYZE", a.id, lead.id, "migration-fixture", "{}");
    String h2 =
      "jdbc:h2:mem:transfer" +
      java.util.UUID.randomUUID() +
      ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    org.flywaydb.core.Flyway.configure().dataSource(h2, "sa", "").load().migrate();
    String schema = "transfer_" + java.util.UUID.randomUUID().toString().replace("-", "");
    org.flywaydb.core.Flyway.configure()
      .dataSource(PG.getPostgresDatabase())
      .schemas(schema)
      .defaultSchema(schema)
      .load()
      .migrate();
    try (
      var origin = PG.getPostgresDatabase().getConnection();
      var copy = java.sql.DriverManager.getConnection(h2, "sa", "");
      var destination = PG.getPostgresDatabase().getConnection()
    ) {
      br.com.wezttech.prospectgt.operacao.MigrarBancoLocal.copiar(origin, copy);
      destination.setSchema(schema);
      var counts = br.com.wezttech.prospectgt.operacao.MigrarBancoLocal.copiar(copy, destination);
      assertThat(counts.get("app_user")).isEqualTo(2L);
      assertThat(counts.get("feedback")).isEqualTo(1L);
      assertThat(counts.get("work_job")).isEqualTo(1L);
      try (
        var statement = destination.createStatement();
        var result = statement.executeQuery("SELECT owner_id,state FROM lead")
      ) {
        assertThat(result.next()).isTrue();
        assertThat(result.getObject(1)).isEqualTo(a.id);
        assertThat(result.getString(2)).isEqualTo("FOLLOW_UP");
      }
      try (
        var statement = destination.prepareStatement(
          "SELECT password_hash FROM app_user WHERE id=?"
        )
      ) {
        statement.setObject(1, a.id);
        try (var result = statement.executeQuery()) {
          assertThat(result.next()).isTrue();
          assertThat(result.getString(1)).isEqualTo(a.passwordHash);
        }
      }
      assertThatThrownBy(() ->
        br.com.wezttech.prospectgt.operacao.MigrarBancoLocal.copiar(copy, destination)
      )
        .isInstanceOf(java.sql.SQLException.class)
        .hasMessageContaining("contém dados");
      try (
        var statement = destination.createStatement();
        var result = statement.executeQuery("SELECT COUNT(*) FROM lead")
      ) {
        assertThat(result.next()).isTrue();
        assertThat(result.getLong(1)).isEqualTo(1L);
      }
    }
  }

  private static final EmbeddedPostgres PG = start();

  private static EmbeddedPostgres start() {
    try {
      return EmbeddedPostgres.builder().setPort(0).start();
    } catch (Exception ex) {
      throw new ExceptionInInitializerError(ex);
    }
  }

  @DynamicPropertySource
  static void postgres(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> PG.getJdbcUrl("postgres", "postgres"));
    registry.add("spring.datasource.username", () -> "postgres");
    registry.add("spring.datasource.password", () -> "");
  }

  @AfterAll
  static void shutdown() throws Exception {
    PG.close();
  }

  @Test
  void migracoesValidadasNoPostgres() throws Exception {
    try (
      var con = PG.getPostgresDatabase().getConnection();
      var statement = con.createStatement()
    ) {
      var r = statement.executeQuery(
        "select count(*) from information_schema.tables where table_schema='public' and table_name in ('lead','app_user','audit_event','work_job','feedback')"
      );
      assertThat(r.next()).isTrue();
      assertThat(r.getInt(1)).isEqualTo(5);
    }
  }

  @Test
  void migracaoPreservaOrigemDeWhatsAppExtraidoDoSite() throws Exception {
    var schema = "upgrade_" + java.util.UUID.randomUUID().toString().replace("-", "");
    var before = org.flywaydb.core.Flyway.configure()
      .dataSource(PG.getPostgresDatabase())
      .schemas(schema)
      .defaultSchema(schema)
      .target("1")
      .load();
    before.migrate();
    try (var con = PG.getPostgresDatabase().getConnection(); var s = con.createStatement()) {
      s.execute(
        "INSERT INTO " +
          schema +
          ".lead (id,source_key,name,category,source_url,source_name,fetched_at,updated_at,whatsapp,analyzed_at,analysis_details) VALUES ('" +
          java.util.UUID.randomUUID() +
          "','legacy','Teste','cafes','https://example.com','fixture',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'5561999990000',CURRENT_TIMESTAMP,'{\"whatsappPublished\":true}')"
      );
    }
    org.flywaydb.core.Flyway.configure()
      .dataSource(PG.getPostgresDatabase())
      .schemas(schema)
      .defaultSchema(schema)
      .load()
      .migrate();
    try (
      var con = PG.getPostgresDatabase().getConnection();
      var s = con.createStatement();
      var r = s.executeQuery("SELECT source_whatsapp,site_whatsapp FROM " + schema + ".lead")
    ) {
      assertThat(r.next()).isTrue();
      assertThat(r.getString(1)).isNull();
      assertThat(r.getString(2)).isEqualTo("5561999990000");
    }
  }
}
