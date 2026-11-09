package br.com.wezttech.prospectgt.analise;

import br.com.wezttech.prospectgt.empresas.*;
import br.com.wezttech.prospectgt.mensagens.MensagemService;
import br.com.wezttech.prospectgt.trabalho.*;
import br.com.wezttech.prospectgt.web.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AnaliseService {

  private final EmpresaRepository leads;
  private final TarefaService jobs;
  private final LeitorSitePublico reader;
  private final ObjectMapper mapper;
  private final Clock clock;
  private final TransactionTemplate tx;

  public AnaliseService(
    EmpresaRepository e,
    TarefaService j,
    LeitorSitePublico r,
    ObjectMapper m,
    Clock c,
    PlatformTransactionManager manager
  ) {
    leads = e;
    jobs = j;
    reader = r;
    mapper = m;
    clock = c;
    tx = new TransactionTemplate(manager);
  }

  public Tarefa solicitar(UUID id, UUID user) {
    var lead = leads.findById(id).orElseThrow(ApiException::missing);
    return jobs.criar(
      "ANALYZE",
      user,
      id,
      "analyze:" + id + ":" + Objects.toString(lead.website, "none"),
      "{}"
    );
  }

  public String executar(Tarefa job) throws Exception {
    var e = leads.findById(job.leadId).orElseThrow(ApiException::missing);
    var reasons = new ArrayList<String>();
    var details = new LinkedHashMap<String, Object>();
    String state;
    int score = e.phone != null || e.whatsapp != null ? 30 : 10;
    String whatsapp = null;
    if (e.website == null || e.website.isBlank()) {
      state = "NO_WEBSITE";
      score += 25;
      reasons.add("Site não informado na fonte consultada. Pode existir um site em outra fonte.");
      details.put("scope", "Não foi feita uma busca completa na internet.");
    } else {
      try {
        var page = reader.ler(e.website);
        details.put("url", page.url());
        details.put("httpStatus", page.code());
        details.put(
          "scope",
          "Leitura de HTML público; sem avaliação estética ou teste de navegação."
        );
        if (page.code() < 200 || page.code() >= 300) {
          state = "INCONCLUSIVE";
          reasons.add(
            "O site respondeu HTTP " + page.code() + ". Não foi possível avaliar o conteúdo."
          );
          score = e.score;
        } else {
          var doc = Jsoup.parse(page.html(), page.url());
          String text = doc.body() == null ? "" : doc.body().text();
          details.put("title", doc.title());
          details.put("textLength", text.length());
          for (var a : doc.select("a[href]")) {
            var n = MensagemService.whatsappPublicado(a.absUrl("href"));
            if (n != null) {
              whatsapp = n;
              break;
            }
          }
          boolean contact =
            whatsapp != null || !doc.select("a[href^=tel:],a[href^=mailto:],form").isEmpty();
          boolean viewport = !doc.select("meta[name=viewport]").isEmpty();
          details.put("contactFound", contact);
          details.put("viewportDeclared", viewport);
          details.put("whatsappPublished", whatsapp != null);
          if (text.length() < 100 && !doc.select("script[src]").isEmpty()) {
            state = "INCONCLUSIVE";
            score = e.score;
            reasons.add(
              "Conteúdo dependente de scripts. A leitura estática não permite avaliar a página."
            );
          } else {
            state = "COMPLETED";
            score += 10;
            if (!contact) {
              score += 25;
              reasons.add(
                "Não identifiquei link de telefone, e-mail, WhatsApp ou formulário no HTML lido."
              );
            } else reasons.add("Encontrei um caminho de contato no HTML do site.");
            if (!viewport) {
              score += 10;
              reasons.add(
                "Não encontrei declaração de viewport no HTML. Isso não comprova problema de uso no celular."
              );
            }
            if (page.url().startsWith("http://")) {
              score += 5;
              reasons.add("A página consultada foi servida por HTTP.");
            }
            if (whatsapp != null) reasons.add("Link de WhatsApp publicado no próprio site.");
            reasons.add("Prioridade por sinais objetivos; não representa intenção de compra.");
          }
        }
      } catch (Exception ex) {
        state = "INCONCLUSIVE";
        score = e.score;
        reasons.add(
          "Não foi possível concluir a leitura do site com segurança. Tente novamente mais tarde."
        );
        details.put("error", ex.getMessage() == null ? "Falha de leitura" : ex.getMessage());
      }
    }
    final String finalState = state,
      finalWhats = whatsapp;
    final int finalScore = Math.min(100, Math.max(0, score));
    final String r = mapper.writeValueAsString(reasons),
      d = mapper.writeValueAsString(details);
    boolean applied = Boolean.TRUE.equals(
      tx.execute(s -> {
        var current = leads.locked(e.id).orElseThrow();
        if (!Objects.equals(current.website, e.website)) return false;
        current.analysisState = finalState;
        current.score = finalScore;
        current.analysisReasons = r;
        current.analysisDetails = d;
        current.analyzedAt = clock.instant();
        current.updatedAt = clock.instant();
        current.siteWhatsapp = finalWhats;
        current.whatsapp = current.sourceWhatsapp != null ? current.sourceWhatsapp : finalWhats;
        leads.save(current);
        return true;
      })
    );
    return mapper.writeValueAsString(
      Map.of("leadId", e.id, "state", applied ? finalState : "SUPERSEDED")
    );
  }
}
