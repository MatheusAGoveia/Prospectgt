package br.com.wezttech.prospectgt.web;

import br.com.wezttech.prospectgt.analise.*;
import br.com.wezttech.prospectgt.auditoria.*;
import br.com.wezttech.prospectgt.carteira.*;
import br.com.wezttech.prospectgt.contas.*;
import br.com.wezttech.prospectgt.descoberta.*;
import br.com.wezttech.prospectgt.empresas.*;
import br.com.wezttech.prospectgt.mensagens.*;
import br.com.wezttech.prospectgt.trabalho.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.Principal;
import java.time.*;
import java.util.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ApiController {

  private final ContaAtual atual;
  private final ContaRepository contas;
  private final EmpresaRepository leads;
  private final BuscaService search;
  private final TarefaRepository jobs;
  private final ReservaService reservas;
  private final MensagemService messages;
  private final AuditoriaService audit;
  private final EventoRepository events;
  private final FeedbackRepository feedback;
  private final AnaliseService analyses;
  private final ObjectMapper mapper;
  private final CatalogoService catalog;
  private final RegioesService regioes;
  private final PasswordEncoder encoder;

  public ApiController(
    ContaAtual a,
    ContaRepository c,
    EmpresaRepository l,
    BuscaService s,
    TarefaRepository j,
    ReservaService r,
    MensagemService m,
    AuditoriaService au,
    EventoRepository e,
    FeedbackRepository f,
    AnaliseService an,
    ObjectMapper om,
    CatalogoService ca,
    PasswordEncoder en,
    RegioesService regions
  ) {
    atual = a;
    contas = c;
    leads = l;
    search = s;
    jobs = j;
    reservas = r;
    messages = m;
    audit = au;
    events = e;
    feedback = f;
    analyses = an;
    mapper = om;
    catalog = ca;
    regioes = regions;
    encoder = en;
  }

  private Map<String, Object> user(Conta u) {
    return Map.of(
      "id",
      u.id,
      "username",
      u.username,
      "name",
      u.displayName,
      "role",
      u.role,
      "specialty",
      u.specialty,
      "enabled",
      u.enabled
    );
  }

  @GetMapping("/me")
  public Object me(Principal p) {
    return user(atual.get(p));
  }

  @GetMapping("/config")
  public Object config() {
    return Map.of(
      "categories",
      Categorias.LABELS,
      "catalog",
      catalog.status(),
      "priceRange",
      "R$ 500 a R$ 2.000",
      "appVersion",
      "0.2.0",
      "regions",
      regioes.cidades()
    );
  }

  @PostMapping("/search")
  public Object search(Principal p, @RequestBody BuscaService.Entrada input) throws Exception {
    return jobView(search.solicitar(atual.get(p).id, input));
  }

  @GetMapping("/jobs/{id}")
  public Object job(Principal p, @PathVariable UUID id) throws Exception {
    var u = atual.get(p);
    var j = jobs.findById(id).orElseThrow(ApiException::missing);
    if (!j.actorId.equals(u.id) && !u.role.equals("ADMIN")) throw ApiException.forbidden();
    return jobView(j);
  }

  private Map<String, Object> jobView(Tarefa j) throws Exception {
    var m = new LinkedHashMap<String, Object>();
    m.put("id", j.id);
    m.put("status", j.status);
    m.put("kind", j.kind);
    m.put("error", j.error);
    if (j.result != null) m.put("result", mapper.readTree(j.result));
    return m;
  }

  @GetMapping("/leads")
  public Object list(
    Principal p,
    @RequestParam(defaultValue = "") String ids,
    @RequestParam(defaultValue = "false") boolean mine
  ) {
    var u = atual.get(p);
    List<Empresa> data;
    if (!ids.isBlank()) {
      try {
        var requested = Arrays.stream(ids.split(",")).limit(150).map(UUID::fromString).toList();
        data = leads.findAllById(requested);
      } catch (Exception e) {
        throw ApiException.bad("Identificadores inválidos.");
      }
    } else data = mine
      ? leads.findByOwnerIdOrderByUpdatedAtDesc(u.id)
      : leads.findTop300ByOrderByUpdatedAtDesc();
    return data
      .stream()
      .map(e -> view(e, u))
      .toList();
  }

  @GetMapping("/leads/{id}")
  @Transactional
  public Object detail(Principal p, @PathVariable UUID id) {
    var u = atual.get(p);
    var e = leads.locked(id).orElseThrow(ApiException::missing);
    reservas.expire(e);
    leads.save(e);
    audit.registrar(id, u.id, "LEAD_VIEWED", "Ficha consultada.");
    var m = view(e, u);
    m.put("message", messages.preparar(e, u));
    m.put(
      "history",
      events
        .findTop80ByLeadIdOrderByCreatedAtDesc(id)
        .stream()
        .map(x ->
          Map.of(
            "action",
            x.action,
            "detail",
            Objects.toString(x.detail, ""),
            "date",
            x.createdAt,
            "actor",
            x.actorId == null
              ? "Sistema"
              : contas
                  .findById(x.actorId)
                  .map(a -> a.displayName)
                  .orElse("Usuário")
          )
        )
        .toList()
    );
    return m;
  }

  @PostMapping("/leads/{id}/claim")
  public Object claim(Principal p, @PathVariable UUID id) {
    var u = atual.get(p);
    return view(reservas.assumir(id, u), u);
  }

  public record ContactInput(String action, String message) {}

  @PostMapping("/leads/{id}/contact")
  @Transactional
  public Object contact(Principal p, @PathVariable UUID id, @RequestBody ContactInput input) {
    if (
      input.action == null || !Set.of("COPY_MESSAGE", "OPEN_WHATSAPP").contains(input.action)
    ) throw ApiException.bad("Ação inválida.");
    var u = atual.get(p);
    var e = reservas.assumir(id, u);
    String text =
      input.message == null || input.message.isBlank()
        ? messages.preparar(e, u)
        : input.message.strip();
    if (text.length() > 3000) throw ApiException.bad("A mensagem deve ter até 3.000 caracteres.");
    String phone = e.whatsapp == null ? e.phone : e.whatsapp;
    String url = null;
    if (input.action.equals("OPEN_WHATSAPP")) try {
      url = messages.link(phone, text);
    } catch (IllegalArgumentException x) {
      throw ApiException.bad(x.getMessage());
    }
    audit.registrar(
      id,
      u.id,
      input.action,
      input.action.equals("OPEN_WHATSAPP")
        ? "Clique para abrir WhatsApp. Envio não confirmado."
        : "Mensagem copiada; envio não confirmado."
    );
    var result = new LinkedHashMap<String, Object>();
    result.put("message", text);
    result.put("url", url);
    return result;
  }

  public record FeedbackInput(String state, String comment, LocalDate followUp) {}

  @PostMapping("/leads/{id}/feedback")
  public Object feedback(Principal p, @PathVariable UUID id, @RequestBody FeedbackInput input) {
    var u = atual.get(p);
    reservas.registrar(id, u, input.state, input.comment, input.followUp);
    return Map.of("ok", true);
  }

  @PostMapping("/leads/{id}/analyze")
  public Object analyze(Principal p, @PathVariable UUID id) throws Exception {
    return jobView(analyses.solicitar(id, atual.get(p).id));
  }

  public record ReleaseInput(String reason) {}

  @PostMapping("/admin/leads/{id}/release")
  public Object release(Principal p, @PathVariable UUID id, @RequestBody ReleaseInput input) {
    reservas.liberar(id, atual.get(p), input.reason);
    return Map.of("ok", true);
  }

  public record ProfileInput(String name, String specialty) {}

  @PostMapping("/me/profile")
  @Transactional
  public Object profile(Principal p, @RequestBody ProfileInput input) {
    if (
      input.name == null ||
      input.name.isBlank() ||
      input.name.length() > 120 ||
      input.specialty == null ||
      input.specialty.length() > 200
    ) throw ApiException.bad("Informe nome e experiência com até 120/200 caracteres.");
    var u = atual.get(p);
    u.displayName = input.name.strip();
    u.specialty = input.specialty.strip();
    contas.save(u);
    return user(u);
  }

  public record PasswordInput(String current, String password) {}

  @PostMapping("/me/password")
  @Transactional
  public Object password(Principal p, @RequestBody PasswordInput input) {
    var u = atual.get(p);
    if (
      input.password == null ||
      input.password.length() < 12 ||
      input.password.length() > 72 ||
      input.current == null ||
      !encoder.matches(input.current, u.passwordHash)
    ) throw ApiException.bad(
      "Confira a senha atual. A nova senha deve ter entre 12 e 72 caracteres."
    );
    u.passwordHash = encoder.encode(input.password);
    contas.save(u);
    return Map.of("ok", true);
  }

  @GetMapping("/admin/users")
  public Object users() {
    return contas.findAll().stream().map(this::user).toList();
  }

  private LinkedHashMap<String, Object> view(Empresa e, Conta u) {
    var m = new LinkedHashMap<String, Object>();
    m.put("id", e.id);
    m.put("name", e.name);
    m.put("category", e.category);
    m.put("categoryLabel", Categorias.LABELS.getOrDefault(e.category, e.category));
    m.put("city", e.city);
    m.put("address", e.address);
    m.put("lat", e.latitude);
    m.put("lon", e.longitude);
    m.put("website", e.website);
    m.put("phone", e.phone);
    m.put("whatsapp", e.whatsapp);
    m.put("contactName", e.contactName);
    m.put("state", e.state);
    m.put("ownerId", e.ownerId);
    m.put(
      "ownerName",
      e.ownerId == null
        ? null
        : contas
            .findById(e.ownerId)
            .map(x -> x.displayName)
            .orElse("Vendedor")
    );
    m.put("reservedUntil", e.reservedUntil);
    m.put("followUp", e.followUp);
    m.put("score", e.score);
    m.put("analysisState", e.analysisState);
    m.put("analyzedAt", e.analyzedAt);
    m.put("sourceUrl", e.sourceUrl);
    m.put("sourceName", e.sourceName);
    m.put("sourceUpdatedAt", e.sourceUpdatedAt);
    m.put("fetchedAt", e.fetchedAt);
    m.put(
      "canContact",
      Set.of("AVAILABLE", "IN_PROGRESS", "FOLLOW_UP").contains(e.state) &&
        (e.ownerId == null || e.ownerId.equals(u.id))
    );
    m.put("whatsappPublished", e.whatsapp != null);
    try {
      m.put("reasons", mapper.readValue(e.analysisReasons, new TypeReference<List<String>>() {}));
      m.put("analysis", mapper.readTree(e.analysisDetails));
    } catch (Exception ex) {
      m.put("reasons", List.of("Análise pendente."));
    }
    return m;
  }
}
