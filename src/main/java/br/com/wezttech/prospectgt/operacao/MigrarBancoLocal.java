package br.com.wezttech.prospectgt.operacao;

import java.sql.*;
import java.util.*;
import org.flywaydb.core.Flyway;

/** Ferramenta de operação local; não possui endpoint HTTP. Destino deve estar vazio. */
public final class MigrarBancoLocal {

  private static final List<String> TABLES = List.of(
    "app_user",
    "lead",
    "audit_event",
    "feedback",
    "work_job"
  );

  private MigrarBancoLocal() {}

  public static Map<String, Long> copiar(Connection source, Connection destination)
    throws SQLException {
    destination.setAutoCommit(false);
    var counts = new LinkedHashMap<String, Long>();
    try {
      for (String table : TABLES)
        try (
          var statement = destination.createStatement();
          var rows = statement.executeQuery("SELECT COUNT(*) FROM " + table)
        ) {
          rows.next();
          if (rows.getLong(1) != 0) throw new SQLException(
            "O banco de destino contém dados; migração cancelada sem sobrescrever registros."
          );
        }
      for (String table : TABLES)
        try (
          var query = source.createStatement();
          var rows = query.executeQuery("SELECT * FROM " + table)
        ) {
          var columns = rows.getMetaData();
          int size = columns.getColumnCount();
          var names = new ArrayList<String>();
          for (int i = 1; i <= size; i++) names.add(
            "\"" + columns.getColumnName(i).toLowerCase(Locale.ROOT).replace("\"", "\"\"") + "\""
          );
          String sql =
            "INSERT INTO " +
            table +
            " (" +
            String.join(",", names) +
            ") VALUES (" +
            String.join(",", Collections.nCopies(size, "?")) +
            ")";
          long count = 0;
          try (var insert = destination.prepareStatement(sql)) {
            while (rows.next()) {
              for (int i = 1; i <= size; i++) {
                Object value = rows.getObject(i);
                if (value instanceof Clob clob) value = clob.getSubString(
                  1,
                  Math.toIntExact(clob.length())
                );
                if (value == null) insert.setNull(
                  i,
                  columns.getColumnType(i) == Types.CLOB
                    ? Types.LONGVARCHAR
                    : columns.getColumnType(i)
                );
                else insert.setObject(i, value);
              }
              insert.executeUpdate();
              count++;
            }
          }
          counts.put(table, count);
        }
      destination.commit();
      return counts;
    } catch (SQLException | RuntimeException ex) {
      destination.rollback();
      throw ex;
    }
  }

  public static void main(String[] args) throws Exception {
    String url = Objects.requireNonNull(
      System.getenv("MIGRATION_DB_URL"),
      "Destino não configurado."
    );
    ConexaoSegura.validarJdbc(url);
    if (!url.startsWith("jdbc:postgresql:")) throw new IllegalArgumentException(
      "Destino precisa ser PostgreSQL."
    );
    String user = System.getenv("MIGRATION_DB_USER"),
      password = System.getenv("MIGRATION_DB_PASSWORD");
    Flyway.configure()
      .dataSource(url, user, password)
      .locations("classpath:db/migration")
      .load()
      .migrate();
    String sourceUrl =
      "jdbc:h2:file:./data/prospectgt;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;IFEXISTS=TRUE";
    try (
      var source = DriverManager.getConnection(sourceUrl, "sa", "");
      var target = DriverManager.getConnection(url, user, password)
    ) {
      System.out.println(
        new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(
          Map.of("migrated", copiar(source, target))
        )
      );
    }
  }
}
