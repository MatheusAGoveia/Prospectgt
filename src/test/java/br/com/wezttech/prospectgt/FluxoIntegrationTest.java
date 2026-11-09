package br.com.wezttech.prospectgt;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import br.com.wezttech.prospectgt.auditoria.*;
import br.com.wezttech.prospectgt.carteira.*;
import br.com.wezttech.prospectgt.contas.*;
import br.com.wezttech.prospectgt.descoberta.*;
import br.com.wezttech.prospectgt.empresas.*;
import br.com.wezttech.prospectgt.trabalho.*;
import br.com.wezttech.prospectgt.web.ApiException;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
  properties = {
    "spring.datasource.url=jdbc:h2:mem:flow;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "prospectgt.bootstrap-enabled=false",
    "prospectgt.worker-enabled=false",
  }
)
@AutoConfigureMockMvc
class FluxoIntegrationTest {

  @Test
  void encerramentoNaoPodeSerDesfeitoPeloFeedback() {
    reservas.assumir(lead.id, a);
    reservas.registrar(lead.id, a, "DO_NOT_CONTACT", "Pedido para não contatar", null);
    assertThatThrownBy(() ->
      reservas.registrar(lead.id, a, "IN_PROGRESS", "Tentativa de reabrir", null)
    ).isInstanceOf(ApiException.class);
    assertThat(empresas.findById(lead.id).orElseThrow().state).isEqualTo("DO_NOT_CONTACT");
  }

  @Test
  void chaveDeCacheLongaEAnaliseExplicita() {
    String key = "analyze:" + lead.id + ":" + "https://example.com/" + "a".repeat(1200);
    var first = jobs.criar("ANALYZE", a.id, lead.id, key, "{}");
    assertThat(first.cacheKey.length()).isLessThan(100);
    jobs.concluir(first.id, "{\"state\":\"INCONCLUSIVE\"}");
    var retry = jobs.criar("ANALYZE", a.id, lead.id, key, "{}");
    assertThat(retry.status).isEqualTo("PENDING");
  }

  @Test
  void contatoDaFontePodeMudarESerRemovido() {
    var original = new Lugar(
      "fixture:company",
      "Empresa",
      "cafes",
      null,
      null,
      -15.7939,
      -47.8828,
      null,
      null,
      "5561999990000",
      "https://example.com",
      "fixture",
      null
    );
    cadastro.importar(original);
    assertThat(empresas.findById(lead.id).orElseThrow().whatsapp).isEqualTo("5561999990000");
    cadastro.importar(
      new Lugar(
        "fixture:company",
        "Empresa",
        "cafes",
        null,
        null,
        -15.7939,
        -47.8828,
        null,
        null,
        "5561999991111",
        "https://example.com",
        "fixture",
        null
      )
    );
    assertThat(empresas.findById(lead.id).orElseThrow().whatsapp).isEqualTo("5561999991111");
    cadastro.importar(
      new Lugar(
        "fixture:company",
        "Empresa",
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
      )
    );
    assertThat(empresas.findById(lead.id).orElseThrow().whatsapp).isNull();
  }

  @Test
  void retornoRegistradoPeloGestorTemResponsavel() {
    var admin = contas.saveAndFlush(
      new Conta(
        "gestor-retorno",
        "Gestor",
        encoder.encode("test-only-password-789"),
        "ADMIN",
        Instant.now()
      )
    );
    reservas.registrar(
      lead.id,
      admin,
      "FOLLOW_UP",
      "Retorno combinado pelo gestor",
      LocalDate.now().plusDays(2)
    );
    assertThat(empresas.findById(lead.id).orElseThrow().ownerId).isEqualTo(admin.id);
    assertThatThrownBy(() -> reservas.assumir(lead.id, a)).isInstanceOf(ApiException.class);
  }

  @Autowired
  MockMvc mvc;

  @Autowired
  ContaRepository contas;

  @Autowired
  EmpresaRepository empresas;

  @Autowired
  EmpresaService cadastro;

  @Autowired
  ReservaService reservas;

  @Autowired
  EventoRepository eventos;

  @Autowired
  FeedbackRepository feedback;

  @Autowired
  TarefaRepository tarefas;

  @Autowired
  TarefaService jobs;

  @Autowired
  BuscaService busca;

  @Autowired
  PasswordEncoder encoder;

  Conta a, b;
  Empresa lead;

  @BeforeEach
  void setup() {
    tarefas.deleteAll();
    feedback.deleteAll();
    eventos.deleteAll();
    empresas.deleteAll();
    contas.deleteAll();
    a = contas.saveAndFlush(
      new Conta(
        "teste-a",
        "Pessoa A",
        encoder.encode("test-only-password-123"),
        "SELLER",
        Instant.now()
      )
    );
    b = contas.saveAndFlush(
      new Conta(
        "teste-b",
        "Pessoa B",
        encoder.encode("test-only-password-456"),
        "SELLER",
        Instant.now()
      )
    );
    UUID id = cadastro.importar(
      new Lugar(
        "fixture:company",
        "Empresa <teste>",
        "cafes",
        null,
        null,
        -15.7939,
        -47.8828,
        null,
        "(61) 99999-0000",
        null,
        "https://example.com",
        "fixture",
        null
      )
    );
    lead = empresas.findById(id).orElseThrow();
  }

  @Test
  void apiExigeLoginECsrf() throws Exception {
    mvc.perform(get("/api/leads")).andExpect(status().isUnauthorized());
    mvc
      .perform(post("/api/leads/" + lead.id + "/claim").with(user(a.username)))
      .andExpect(status().isForbidden());
    mvc
      .perform(
        post("/api/leads/" + lead.id + "/claim")
          .with(user(a.username))
          .with(csrf())
      )
      .andExpect(status().isOk());
  }

  @Test
  void reservaConcorrenteTemUmVencedor() throws Exception {
    var go = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var f1 = pool.submit(() -> {
        go.await();
        try {
          reservas.assumir(lead.id, a);
          return true;
        } catch (ApiException e) {
          return false;
        }
      });
      var f2 = pool.submit(() -> {
        go.await();
        try {
          reservas.assumir(lead.id, b);
          return true;
        } catch (ApiException e) {
          return false;
        }
      });
      go.countDown();
      int wins = (f1.get() ? 1 : 0) + (f2.get() ? 1 : 0);
      assertThat(wins).isEqualTo(1);
    }
    assertThat(
      eventos
        .findTop80ByLeadIdOrderByCreatedAtDesc(lead.id)
        .stream()
        .filter(e -> e.action.equals("CLAIMED"))
        .count()
    ).isEqualTo(1);
  }

  @Test
  void copiarNaoRenovaEResultadoNaoAlegaEnvio() throws Exception {
    reservas.assumir(lead.id, a);
    Instant until = empresas.findById(lead.id).orElseThrow().reservedUntil;
    mvc
      .perform(
        post("/api/leads/" + lead.id + "/contact")
          .with(user(a.username))
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"action\":\"COPY_MESSAGE\",\"message\":\"Olá, teste!\"}")
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.message").value("Olá, teste!"));
    assertThat(empresas.findById(lead.id).orElseThrow().reservedUntil).isEqualTo(until);
    mvc
      .perform(
        post("/api/leads/" + lead.id + "/contact")
          .with(user(b.username))
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"action\":\"OPEN_WHATSAPP\"}")
      )
      .andExpect(status().isConflict());
    assertThat(eventos.findTop80ByLeadIdOrderByCreatedAtDesc(lead.id)).noneMatch(e ->
      e.action.equals("MESSAGE_SENT")
    );
  }

  @Test
  void feedbackEFechamentoProtegemCarteira() throws Exception {
    reservas.assumir(lead.id, a);
    mvc
      .perform(
        post("/api/leads/" + lead.id + "/feedback")
          .with(user(b.username))
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"state\":\"WON\",\"comment\":\"teste\"}")
      )
      .andExpect(status().isForbidden());
    reservas.registrar(lead.id, a, "WON", "Contrato de teste informado", null);
    assertThat(empresas.findById(lead.id).orElseThrow().state).isEqualTo("WON");
    assertThatThrownBy(() -> reservas.assumir(lead.id, b)).isInstanceOf(ApiException.class);
  }

  @Test
  void upsertNaoApagaReservaOuHistorico() {
    reservas.assumir(lead.id, a);
    cadastro.importar(
      new Lugar(
        "fixture:company",
        "Empresa atualizada",
        "cafes",
        null,
        null,
        -15.7939,
        -47.8828,
        null,
        "(61) 99999-0000",
        null,
        "https://example.com",
        "fixture",
        null
      )
    );
    assertThat(empresas.count()).isEqualTo(1);
    assertThat(empresas.findById(lead.id).orElseThrow().ownerId).isEqualTo(a.id);
    assertThat(eventos.findTop80ByLeadIdOrderByCreatedAtDesc(lead.id)).isNotEmpty();
  }

  @Test
  void buscaERecuperacaoSaoPersistentes() throws Exception {
    var job = busca.solicitar(
      a.id,
      new BuscaService.Entrada(-15.7939, -47.8828, 1, "cafes", "Teste")
    );
    assertThat(tarefas.findById(job.id)).isPresent();
    assertThat(jobs.proxima().status).isEqualTo("RUNNING");
    jobs.recuperar();
    assertThat(tarefas.findById(job.id).orElseThrow().status).isEqualTo("PENDING");
    assertThatThrownBy(() ->
      busca.solicitar(a.id, new BuscaService.Entrada(-15, -47, 100, "cafes", "Teste"))
    ).isInstanceOf(ApiException.class);
  }

  @Test
  void mensagensInvalidasNaoCriamReserva() throws Exception {
    mvc
      .perform(
        post("/api/leads/" + lead.id + "/contact")
          .with(user(a.username))
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content("{}")
      )
      .andExpect(status().isBadRequest());
    assertThat(empresas.findById(lead.id).orElseThrow().ownerId).isNull();
  }

  @Test
  void cacheNaoRenovaValidadeENaoExibeTarefaDeOutroVendedor() throws Exception {
    var original = jobs.criar("SEARCH", a.id, null, "cache-test", "{}");
    jobs.concluir(original.id, "{\"ids\":[]}");
    var copy = jobs.criar("SEARCH", b.id, null, "cache-test", "{}");
    assertThat(copy.id).isNotEqualTo(original.id);
    assertThat(copy.cacheKey).isNull();
    assertThat(copy.status).isEqualTo("COMPLETED");
    mvc
      .perform(get("/api/jobs/" + original.id).with(user(b.username)))
      .andExpect(status().isForbidden());
    var stale = tarefas.findById(original.id).orElseThrow();
    stale.createdAt = Instant.now().minus(Duration.ofHours(25));
    tarefas.saveAndFlush(stale);
    assertThat(jobs.criar("SEARCH", a.id, null, "cache-test", "{}").status).isEqualTo("PENDING");
  }

  @Test
  void mesmoVendedorReutilizaTarefaPendente() {
    var first = jobs.criar("SEARCH", a.id, null, "pending-test", "{}");
    assertThat(jobs.criar("SEARCH", a.id, null, "pending-test", "{}").id).isEqualTo(first.id);
    assertThat(jobs.criar("SEARCH", b.id, null, "pending-test", "{}").id).isNotEqualTo(first.id);
  }

  @Test
  void novoSiteInvalidaDiagnosticoAntigoSemAlterarCarteira() {
    reservas.assumir(lead.id, a);
    var previous = empresas.findById(lead.id).orElseThrow();
    previous.analyzedAt = Instant.now();
    previous.analysisState = "COMPLETED";
    previous.analysisDetails = "{\"old\":true}";
    empresas.saveAndFlush(previous);
    cadastro.importar(
      new Lugar(
        "fixture:company",
        "Empresa atualizada",
        "cafes",
        null,
        null,
        -15.7939,
        -47.8828,
        "https://new.example.com",
        "(61) 99999-0000",
        null,
        "https://example.com",
        "fixture",
        null
      )
    );
    var updated = empresas.findById(lead.id).orElseThrow();
    assertThat(updated.analyzedAt).isNull();
    assertThat(updated.analysisState).isEqualTo("PENDING");
    assertThat(updated.analysisDetails).isEqualTo("{}");
    assertThat(updated.ownerId).isEqualTo(a.id);
  }

  @Test
  void reabrirExigeGestorEMotivo() throws Exception {
    mvc
      .perform(
        post("/api/admin/leads/" + lead.id + "/release")
          .with(user(a.username))
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"reason\":\"teste\"}")
      )
      .andExpect(status().isForbidden());
    var admin = contas.saveAndFlush(
      new Conta(
        "gestor-teste",
        "Gestor",
        encoder.encode("test-only-password-789"),
        "ADMIN",
        Instant.now()
      )
    );
    mvc
      .perform(
        post("/api/admin/leads/" + lead.id + "/release")
          .with(user(admin.username).roles("ADMIN"))
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content("{}")
      )
      .andExpect(status().isBadRequest());
  }
}
