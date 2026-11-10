package br.com.wezttech.prospectgt;

import static org.assertj.core.api.Assertions.*;

import br.com.wezttech.prospectgt.analise.PoliticaUrlPublica;
import br.com.wezttech.prospectgt.carteira.DiasUteis;
import br.com.wezttech.prospectgt.descoberta.*;
import br.com.wezttech.prospectgt.mensagens.MensagemService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RegrasTest {

  @Test
  void trocaDeSnapshotAtualizaDadosECoberturaSemExigirArquivoLegado(@TempDir Path dir)
    throws Exception {
    var mapper = new ObjectMapper().findAndRegisterModules();
    var configured = dir.resolve("catalog.jsonl");
    var first = dir.resolve("catalogos/snapshots/first/catalog.jsonl");
    var second = dir.resolve("catalogos/snapshots/second/catalog.jsonl");
    for (var entry : List.of(first, second)) {
      Files.createDirectories(entry.getParent());
      var place = new Lugar(
        "osm:node:1",
        entry.getParent().getFileName().toString(),
        "cafes",
        null,
        null,
        -19.9668,
        -44.2008,
        null,
        null,
        null,
        "https://www.openstreetmap.org/node/1",
        "fixture",
        null
      );
      Files.writeString(entry, mapper.writeValueAsString(place) + "\n");
      Files.writeString(
        entry.resolveSibling("catalog.jsonl.coverage.poly"),
        "Test\n1\n-45 -21\n-43 -21\n-43 -19\n-45 -19\nEND\nEND\n"
      );
      Files.writeString(entry.resolveSibling("catalog.jsonl.meta.json"), "{\"records\":1}");
    }
    var pointer = dir.resolve("catalog.jsonl.snapshot.json");
    Files.writeString(pointer, "{\"path\":\"catalogos/snapshots/first/catalog.jsonl\"}");
    var catalog = new CatalogoService(configured.toString(), mapper);
    assertThat(catalog.cobre(-19.9668, -44.2008)).isTrue();
    assertThat(catalog.consultar(-19.9668, -44.2008, 1, "cafes").getFirst().name()).isEqualTo(
      "first"
    );
    String version = catalog.versao();
    Files.writeString(pointer, "{\"path\":\"catalogos/snapshots/second/catalog.jsonl\"}");
    assertThat(catalog.consultar(-19.9668, -44.2008, 1, "cafes").getFirst().name()).isEqualTo(
      "second"
    );
    assertThat(catalog.versao()).isNotEqualTo(version);
  }

  @Test
  void cloudRecusaConexaoSemTlsVerificadoOuCredencialNaUrl() {
    String secure =
      "jdbc:postgresql://example.com/database?sslmode=verify-full&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory";
    br.com.wezttech.prospectgt.operacao.ConexaoSegura.validarJdbc(secure);
    assertThat(org.postgresql.Driver.parseURL(secure, null).getProperty("sslmode"))
      .isEqualTo("verify-full");
    for (String unsafe : List.of(
      "jdbc:h2:mem:cloud",
      secure.replace("verify-full", "disable"),
      secure.replace("verify-full", "require"),
      secure + "&sslmode=disable",
      secure + "&password=secret",
      secure + "#x&sslmode=disable",
      secure.replace("sslmode", "%73slmode"),
      secure.replace("sslfactory", "%73slfactory"),
      secure.replace("DefaultJavaSSLFactory", "NonValidatingFactory")
    ))
      assertThatThrownBy(() ->
        br.com.wezttech.prospectgt.operacao.ConexaoSegura.validarJdbc(unsafe)
      ).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void mensagemPersonalizaRamoSemAfirmarQueSiteNaoExiste() {
    var e = new br.com.wezttech.prospectgt.empresas.Empresa();
    e.name = "Padaria de teste";
    e.category = "padarias";
    var user = new br.com.wezttech.prospectgt.contas.Conta(
      "teste",
      "Matheus",
      "hash",
      "SELLER",
      Instant.now()
    );
    String text = new MensagemService().preparar(e, user);
    assertThat(text).contains(
      "Padaria de teste",
      "Matheus",
      "cadastro público",
      "não informa um site",
      "encomendas",
      "landing page"
    );
    assertThat(text).doesNotContain("vocês não têm site", "site precisa de melhoria");
  }

  @Test
  void mensagemUsaSomenteDiagnosticoConcluido() {
    var e = new br.com.wezttech.prospectgt.empresas.Empresa();
    e.name = "Empresa de teste";
    e.category = "oficinas";
    e.website = "https://example.com";
    e.analysisDetails = "{\"contactFound\":false}";
    var user = new br.com.wezttech.prospectgt.contas.Conta(
      "teste",
      "Vendedor 1",
      "hash",
      "SELLER",
      Instant.now()
    );
    e.analysisState = "INCONCLUSIVE";
    assertThat(new MensagemService().preparar(e, user)).doesNotContain("não encontrei");
    e.analysisState = "COMPLETED";
    assertThat(new MensagemService().preparar(e, user)).contains(
      "não encontrei",
      "conteúdo consultado",
      "orçamento"
    );
  }

  @Test
  void tresDiasUteisAposSexta() {
    assertThat(DiasUteis.somar(Instant.parse("2026-11-06T15:00:00Z"), 3)).isEqualTo(
      Instant.parse("2026-11-11T15:00:00Z")
    );
  }

  @Test
  void retornoSextaTemToleranciaAteSegunda() {
    assertThat(DiasUteis.depoisDoRetorno(LocalDate.of(2026, 11, 6))).isEqualTo(
      Instant.parse("2026-11-10T02:59:00Z")
    );
  }

  @Test
  void urlsInternasOuProtocolosIndevidosBloqueados() throws Exception {
    for (var u : List.of(
      "http://localhost/a",
      "http://127.0.0.1/",
      "http://[::1]/",
      "http://169.254.169.254/",
      "file:///etc/passwd",
      "https://user:pass@example.com/",
      "http://example.com:8080/",
      "http://intranet.local/"
    ))
      assertThatThrownBy(() -> PoliticaUrlPublica.validar(u)).isInstanceOf(
        java.io.IOException.class
      );
    for (var a : List.of(
      "127.0.0.1",
      "10.1.2.3",
      "192.168.1.1",
      "169.254.169.254",
      "::1",
      "fc00::1",
      "100.64.0.1"
    ))
      assertThatThrownBy(() ->
        PoliticaUrlPublica.validarEndereco(InetAddress.getByName(a))
      ).isInstanceOf(java.io.IOException.class);
    assertThat(PoliticaUrlPublica.validar("example.com").getScheme()).isEqualTo("https");
    assertThatThrownBy(() -> PoliticaUrlPublica.resolver("127.0.0.1")).isInstanceOf(
      UnknownHostException.class
    );
  }

  @Test
  void whatsappMantemApenasNumerosValidosETextoCodificado() {
    assertThat(MensagemService.numero("(61) 99999-0000")).isEqualTo("5561999990000");
    assertThat(MensagemService.numero("123")).isNull();
    assertThat(MensagemService.whatsappPublicado("https://wa.me/5561999990000")).isEqualTo(
      "5561999990000"
    );
    assertThat(
      MensagemService.whatsappPublicado("https://evil.example/?phone=5561999990000")
    ).isNull();
    assertThat(new MensagemService().link("61999990000", "Olá & teste\n")).contains(
      "Ol%C3%A1+%26+teste"
    );
  }

  @Test
  void catalogoUsaPoligonoEObjetosReaisDoArquivo(@TempDir Path dir) throws Exception {
    var file = dir.resolve("catalog.jsonl");
    var mapper = new ObjectMapper().findAndRegisterModules();
    var place = new Lugar(
      "test:1",
      "Empresa de teste",
      "cafes",
      null,
      null,
      -15.7939,
      -47.8828,
      null,
      null,
      null,
      "https://example.com",
      "fixture",
      null
    );
    Files.writeString(file, mapper.writeValueAsString(place) + "\n");
    Files.writeString(
      dir.resolve("catalog.jsonl.coverage.poly"),
      "Teste\n1\n-48 -16\n-47 -16\n-47 -15\n-48 -15\n-48 -16\nEND\nEND\n"
    );
    var catalog = new CatalogoService(file.toString(), mapper);
    assertThat(catalog.cobre(-15.7939, -47.8828)).isTrue();
    assertThat(catalog.cobre(-23.56, -46.69)).isFalse();
    assertThat(catalog.consultar(-15.7939, -47.8828, 1, "cafes")).hasSize(1);
    assertThat(catalog.consultar(-15.7939, -47.8828, 1, "padarias")).isEmpty();
  }

  @Test
  void coberturaCombinadaMantemMinasECentroOesteSemInventarCoberturaNoMeio(@TempDir Path dir)
    throws Exception {
    var file = dir.resolve("catalog.jsonl");
    Files.writeString(file, "");
    Files.writeString(
      dir.resolve("catalog.jsonl.coverage.poly"),
      "Test\n1\n-48 -16\n-47 -16\n-47 -15\n-48 -15\nEND\n2\n-45 -21\n-43 -21\n-43 -19\n-45 -19\nEND\nEND\n"
    );
    var catalog = new CatalogoService(file.toString(), new ObjectMapper());
    assertThat(catalog.cobre(-15.7939, -47.8828)).isTrue();
    assertThat(catalog.cobre(-19.9668, -44.2008)).isTrue();
    assertThat(catalog.cobre(-19.9321, -44.0539)).isTrue();
    assertThat(catalog.cobre(-19.9102, -43.9266)).isTrue();
    assertThat(catalog.cobre(-18, -46)).isFalse();
  }
}
