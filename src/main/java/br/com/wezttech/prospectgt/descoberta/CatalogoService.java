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

  public CatalogoService(@Value("${prospectgt.catalog-path}") String path, ObjectMapper mapper) {
    this.path = Path.of(path);
    this.mapper = mapper;
  }

  public synchronized List<Lugar> consultar(double lat, double lon, double km, String category)
    throws IOException {
    if (!Files.exists(path)) return List.of();
    long modified = Files.getLastModifiedTime(path).toMillis();
    if (places == null || modified != loadedTime) {
      var result = new ArrayList<Lugar>();
      try (var r = Files.newBufferedReader(path)) {
        String line;
        while ((line = r.readLine()) != null)
          if (!line.isBlank()) result.add(mapper.readValue(line, Lugar.class));
      }
      places = List.copyOf(result);
      loadedTime = modified;
    }
    return places
      .stream()
      .filter(p -> p.category().equals(category) && distancia(lat, lon, p.lat(), p.lon()) <= km)
      .sorted(Comparator.comparingDouble(p -> distancia(lat, lon, p.lat(), p.lon())))
      .limit(150)
      .toList();
  }

  public boolean cobre(double lat, double lon) {
    if (!Files.exists(path)) return false;
    Path polygon = path.resolveSibling(path.getFileName() + ".coverage.poly");
    if (!Files.exists(polygon)) return false;
    try {
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
    result.put("ready", Files.exists(path));
    Path meta = path.resolveSibling(path.getFileName() + ".meta.json");
    if (Files.exists(meta)) try {
      result.putAll(
        mapper.readValue(
          meta.toFile(),
          new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {}
        )
      );
    } catch (IOException ignored) {}
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
