package br.com.wezttech.prospectgt.descoberta;

import com.fasterxml.jackson.databind.ObjectMapper;
import crosby.binary.osmosis.OsmosisReader;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.openstreetmap.osmosis.core.container.v0_6.*;
import org.openstreetmap.osmosis.core.domain.v0_6.*;
import org.openstreetmap.osmosis.core.task.v0_6.Sink;

public final class PbfCatalogImporter {

  private record WayData(long id, Map<String, String> tags, List<Long> refs, Instant updated) {}

  private interface Visitor {
    void visit(Entity e);
  }

  private static void read(Path file, Visitor visitor) throws IOException {
    {
      var reader = new OsmosisReader(file.toFile());
      reader.setSink(
        new Sink() {
          public void initialize(Map<String, Object> meta) {}

          public void complete() {}

          public void close() {}

          public void process(EntityContainer c) {
            visitor.visit(c.getEntity());
          }
        }
      );
      reader.run();
    }
  }

  private static Map<String, String> tags(Entity e) {
    var t = new HashMap<String, String>();
    for (Tag x : e.getTags()) t.put(x.getKey(), x.getValue());
    return t;
  }

  public static Map<String, Object> importar(Path input, Path output, String region)
    throws IOException {
    var places = new ArrayList<Lugar>();
    var ways = new ArrayList<WayData>();
    var wanted = new HashSet<Long>();
    read(input, e -> {
      if (!(e instanceof Node) && !(e instanceof Way)) return;
      var t = tags(e);
      if (
        Categorias.identificar(t) == null || (!t.containsKey("name") && !t.containsKey("brand"))
      ) return;
      Instant updated = e.getTimestamp() == null ? null : e.getTimestamp().toInstant();
      if (e instanceof Node n) {
        var p = Lugar.deTags(
          "node",
          n.getId(),
          t,
          n.getLatitude(),
          n.getLongitude(),
          updated,
          "OpenStreetMap / Geofabrik"
        );
        if (p != null) places.add(p);
      }
      if (e instanceof Way w) {
        var refs = w.getWayNodes().stream().map(WayNode::getNodeId).toList();
        wanted.addAll(refs);
        ways.add(new WayData(w.getId(), t, refs, updated));
      }
    });
    System.out.println(
      "Catálogo: " +
        places.size() +
        " pontos e " +
        ways.size() +
        " áreas encontradas. Calculando centros..."
    );
    var positions = new HashMap<Long, double[]>();
    read(input, e -> {
      if (e instanceof Node n && wanted.contains(n.getId())) positions.put(n.getId(), new double[] {
        n.getLatitude(),
        n.getLongitude(),
      });
    });
    for (var w : ways) {
      double lat = 0,
        lon = 0;
      int count = 0;
      for (long ref : w.refs) {
        var p = positions.get(ref);
        if (p != null) {
          lat += p[0];
          lon += p[1];
          count++;
        }
      }
      if (count > 0) {
        var p = Lugar.deTags(
          "way",
          w.id,
          w.tags,
          lat / count,
          lon / count,
          w.updated,
          "OpenStreetMap / Geofabrik"
        );
        if (p != null) places.add(p);
      }
    }
    Files.createDirectories(output.toAbsolutePath().getParent());
    Path temp = output.resolveSibling(output.getFileName() + ".tmp");
    var mapper = new ObjectMapper().findAndRegisterModules();
    mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    try (var writer = Files.newBufferedWriter(temp)) {
      for (var p : places) {
        writer.write(mapper.writeValueAsString(p));
        writer.newLine();
      }
    }
    Files.move(temp, output, StandardCopyOption.REPLACE_EXISTING);
    var counts = new TreeMap<String, Long>();
    for (var p : places) counts.merge(p.category(), 1L, Long::sum);
    long phones = places
      .stream()
      .filter(p -> p.phone() != null || p.whatsapp() != null)
      .count();
    var meta = new LinkedHashMap<String, Object>();
    meta.put("region", region);
    meta.put("preparedAt", Instant.now().toString());
    meta.put("records", places.size());
    meta.put("withContact", phones);
    meta.put("categories", counts);
    meta.put("license", "ODbL 1.0 — © OpenStreetMap contributors");
    meta.put("source", "https://download.geofabrik.de/south-america/brazil/centro-oeste.html");
    mapper
      .writerWithDefaultPrettyPrinter()
      .writeValue(output.resolveSibling(output.getFileName() + ".meta.json").toFile(), meta);
    System.out.println(mapper.writeValueAsString(meta));
    return meta;
  }

  public static void main(String[] args) throws Exception {
    if (args.length < 2) throw new IllegalArgumentException(
      "Uso: arquivo.osm.pbf catalogo.jsonl [região]"
    );
    importar(Path.of(args[0]), Path.of(args[1]), args.length > 2 ? args[2] : "Centro-Oeste");
  }
}
