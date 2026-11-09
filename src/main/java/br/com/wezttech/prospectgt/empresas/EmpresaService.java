package br.com.wezttech.prospectgt.empresas;

import br.com.wezttech.prospectgt.descoberta.Lugar;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmpresaService {

  private final EmpresaRepository empresas;
  private final Clock clock;
  private final ObjectMapper mapper;

  public EmpresaService(EmpresaRepository e, Clock c, ObjectMapper m) {
    empresas = e;
    clock = c;
    mapper = m;
  }

  @Transactional
  public UUID importar(Lugar p) {
    var e = empresas.findBySourceKey(p.key()).orElseGet(() -> {
      var n = new Empresa();
      n.id = UUID.randomUUID();
      n.sourceKey = p.key();
      return n;
    });
    e.name = limit(p.name(), 240);
    e.category = p.category();
    e.city = limit(p.city(), 150);
    e.address = limit(p.address(), 500);
    String nextWebsite = limit(p.website(), 1500);
    if (!Objects.equals(e.website, nextWebsite)) {
      e.analyzedAt = null;
      e.analysisState = "PENDING";
      e.analysisDetails = "{}";
      e.siteWhatsapp = null;
    }
    e.latitude = p.lat();
    e.longitude = p.lon();
    e.website = nextWebsite;
    e.phone = limit(p.phone(), 100);
    e.sourceWhatsapp = limit(p.whatsapp(), 100);
    e.whatsapp = e.sourceWhatsapp != null ? e.sourceWhatsapp : e.siteWhatsapp;
    e.sourceUrl = p.sourceUrl();
    e.sourceName = p.sourceName();
    e.sourceUpdatedAt = p.sourceUpdatedAt();
    e.fetchedAt = clock.instant();
    e.updatedAt = clock.instant();
    if (e.analyzedAt == null) {
      var reasons = new ArrayList<String>();
      int score = 10;
      if (e.phone != null || e.whatsapp != null) {
        score += 20;
        reasons.add("Contato publicado na fonte; validade ainda não verificada.");
      }
      if (e.website == null) {
        score += 25;
        reasons.add("Site não informado na fonte. Isso não comprova que a empresa não tenha site.");
      } else {
        score += 10;
        reasons.add("Site informado, aguardando análise.");
      }
      e.score = score;
      try {
        e.analysisReasons = mapper.writeValueAsString(reasons);
      } catch (Exception ex) {
        throw new IllegalStateException(ex);
      }
    }
    empresas.save(e);
    return e.id;
  }

  private String limit(String s, int max) {
    return s == null || s.isBlank()
      ? null
      : s.strip().substring(0, Math.min(s.strip().length(), max));
  }
}
