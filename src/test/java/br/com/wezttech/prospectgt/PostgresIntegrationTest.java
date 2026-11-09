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
