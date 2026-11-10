package br.com.wezttech.prospectgt.descoberta;

import br.com.wezttech.prospectgt.empresas.*;
import br.com.wezttech.prospectgt.trabalho.*;
import br.com.wezttech.prospectgt.web.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class BuscaService {

  public record Entrada(double lat, double lon, double radius, String category, String region) {}

  private final CatalogoService catalog;
  private final FonteOverpass overpass;
  private final EmpresaService empresas;
  private final TarefaService tarefas;
  private final ObjectMapper mapper;

  public BuscaService(
    CatalogoService c,
    FonteOverpass o,
    EmpresaService e,
    TarefaService t,
    ObjectMapper m
  ) {
    catalog = c;
    overpass = o;
    empresas = e;
    tarefas = t;
    mapper = m;
  }

  public Tarefa solicitar(UUID user, Entrada e) throws Exception {
    if (
      !Double.isFinite(e.lat) ||
      !Double.isFinite(e.lon) ||
      !Double.isFinite(e.radius) ||
      e.lat < -34 ||
      e.lat > 6 ||
      e.lon < -74 ||
      e.lon > -34 ||
      e.radius < 0.5 ||
      e.radius > 10 ||
      !Categorias.LABELS.containsKey(e.category)
    ) throw ApiException.bad("Escolha uma região no Brasil, um ramo e um raio entre 0,5 e 10 km.");
    String cache = String.format(
      Locale.ROOT,
      "search:" + catalog.versao() + ":%.4f:%.4f:%.1f:%s",
      e.lat,
      e.lon,
      e.radius,
      e.category
    );
    return tarefas.criar("SEARCH", user, null, cache, mapper.writeValueAsString(e));
  }

  public String executar(Tarefa job) throws Exception {
    var input = mapper.readValue(job.payload, Entrada.class);
    List<Lugar> found;
    String source;
    if (catalog.cobre(input.lat, input.lon)) {
      found = catalog.consultar(input.lat, input.lon, input.radius, input.category);
      source = "Catálogo regional OpenStreetMap / Geofabrik";
    } else {
      found = overpass.buscar(input.lat, input.lon, input.radius, input.category);
      source = "OpenStreetMap / Overpass";
    }
    var ids = new ArrayList<UUID>();
    int autoAnalyses = 0;
    for (var p : found) {
      var id = empresas.importar(p);
      ids.add(id);
      if (p.website() != null && autoAnalyses < 5) {
        tarefas.criar("ANALYZE", job.actorId, id, "analyze:" + id + ":" + p.website(), "{}");
        autoAnalyses++;
      }
    }
    return mapper.writeValueAsString(
      Map.of(
        "ids",
        ids,
        "source",
        source,
        "count",
        ids.size(),
        "region",
        input.region == null ? "Área selecionada" : input.region,
        "limit",
        150,
        "notice",
        "Dados publicados, sujeitos a lacunas e desatualização. Telefones não são automaticamente WhatsApp."
      )
    );
  }
}
