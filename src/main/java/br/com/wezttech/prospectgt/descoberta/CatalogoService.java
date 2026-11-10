package br.com.wezttech.prospectgt.descoberta;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CatalogoService {

  private final Path path;
  private final ObjectMapper mapper;
  private volatile List<Lugar> places;
  private volatile long loadedTime;
  private volatile Path loadedPath;

  private Path ativo() throws IOException {
    Path pointer = path.resolveSibling(path.getFileName() + ".snapshot.json");
    if (!Files.exists(pointer)) return path;
    var manifest = mapper.readTree(pointer.toFile());
    if (manifest == null || !manifest.path("path").isTextual()) throw new IOException(
      "Manifesto de catálogo inválido."
    );
    Path parent = path.toAbsolutePath().getParent();
    Path active = parent.resolve(manifest.path("path").asText()).normalize();
    if (
      !active.startsWith(parent.resolve("catalogos/snapshots").normalize()) ||
      !active.getFileName().equals(path.getFileName())
    ) throw new IOException("Snapshot fora do diretório de catálogos.");
    for (String suffix : List.of("", ".coverage.poly", ".meta.json"))
      if (
        !Files.isRegularFile(active.resolveSibling(active.getFileName() + suffix))
      ) throw new IOException("Snapshot de catálogo incompleto.");
    return active;
  }

  public CatalogoService(@Value("${prospectgt.catalog-path}") String path, ObjectMapper mapper) {
    this.path = Path.of(path);
    this.mapper = mapper;
  }

  public synchronized List<Lugar> consultar(double lat, double lon, double km, String category)
    throws IOException {
    Path active = ativo();
    if (!Files.exists(active)) return List.of();
    long modified = Files.getLastModifiedTime(active).toMillis();
    if (places == null || modified != loadedTime || !active.equals(loadedPath)) {
      var result = new ArrayList<Lugar>();
      try (var r = Files.newBufferedReader(active)) {
        String line;
        while ((line = r.readLine()) != null)
          if (!line.isBlank()) result.add(mapper.readValue(line, Lugar.class));
      }
      places = List.copyOf(result);
      loadedTime = modified;
      loadedPath = active;
    }
    return places
      .stream()
      .filter(p -> p.category().equals(category) && distancia(lat, lon, p.lat(), p.lon()) <= km)
      .sorted(Comparator.comparingDouble(p -> distancia(lat, lon, p.lat(), p.lon())))
      .limit(150)
      .toList();
  }

  public boolean cobre(double lat, double lon) {
    try {
      Path active = ativo();
      if (!Files.exists(active)) return false;
      Path polygon = active.resolveSibling(active.getFileName() + ".coverage.poly");
      if (!Files.exists(polygon)) return false;
      var lines = Files.readAllLines(polygon);
      var ring = new ArrayList<double[]>();
      boolean inside = false,
        hole = false,
        reading = false;
      for (int i = 1; i < lines.size(); i++) {
        String line = lines.get(i).strip();
        if (line.isBlank()) continue;
        if (line.equals("END")) {
          if (reading && contem(ring, lat, lon)) {
            if (hole) return false;
            inside = true;
          }
          ring.clear();
          reading = false;
          continue;
        }
        if (!reading) {
          hole = line.startsWith("!");
          reading = true;
          continue;
        }
        var pair = line.split("\\s+");
        if (pair.length >= 2) ring.add(new double[] {
          Double.parseDouble(pair[1]),
          Double.parseDouble(pair[0]),
        });
      }
      return inside;
    } catch (Exception e) {
      return false;
    }
  }

  public String versao() {
    try {
      Path active = ativo();
      return (
        active.getParent().getFileName() +
        ":" +
        Files.getLastModifiedTime(active).toMillis() +
        ":" +
        Files.size(active)
      );
    } catch (IOException ignored) {
      return "online";
    }
  }

  private static boolean contem(List<double[]> points, double lat, double lon) {
    boolean result = false;
    for (int i = 0, j = points.size() - 1; i < points.size(); j = i++) {
      var a = points.get(i);
      var b = points.get(j);
      if (
        a[0] > lat != b[0] > lat && lon < ((b[1] - a[1]) * (lat - a[0])) / (b[0] - a[0]) + a[1]
      ) result = !result;
    }
    return result;
  }

  public Map<String, Object> status() {
    var result = new LinkedHashMap<String, Object>();
    try {
      Path active = ativo();
      result.put("ready", Files.exists(active));
      Path meta = active.resolveSibling(active.getFileName() + ".meta.json");
      if (Files.exists(meta)) {
        result.putAll(
          mapper.readValue(
            meta.toFile(),
            new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {}
          )
        );
      }
    } catch (IOException ignored) {
      result.put("ready", false);
    }
    return result;
  }

  public static double distancia(double a, double b, double c, double d) {
    double x = Math.toRadians(c - a),
      y = Math.toRadians(d - b);
    double z =
      Math.sin(x / 2) * Math.sin(x / 2) +
      Math.cos(Math.toRadians(a)) * Math.cos(Math.toRadians(c)) * Math.sin(y / 2) * Math.sin(y / 2);
    return 6371 * 2 * Math.atan2(Math.sqrt(z), Math.sqrt(Math.max(0, 1 - z)));
  }
}
