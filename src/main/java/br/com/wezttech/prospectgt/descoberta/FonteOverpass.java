package br.com.wezttech.prospectgt.descoberta;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.*;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class FonteOverpass {

  private final String endpoint;
  private final ObjectMapper mapper;

  public FonteOverpass(@Value("${prospectgt.overpass-url}") String url, ObjectMapper mapper) {
    this.endpoint = url;
    this.mapper = mapper;
  }

  public List<Lugar> buscar(double lat, double lon, double km, String category) throws Exception {
    String query =
      "[out:json][timeout:15];nwr(around:" +
      (int) (km * 1000) +
      "," +
      lat +
      "," +
      lon +
      ")" +
      Categorias.query(category) +
      ";out center tags 150;";
    var post = new HttpPost(endpoint);
    post.setHeader(
      "User-Agent",
      "Prospectgt/0.1 (internal sales pilot; limited interactive requests)"
    );
    post.setEntity(
      new StringEntity(
        "data=" + URLEncoder.encode(query, StandardCharsets.UTF_8),
        ContentType.APPLICATION_FORM_URLENCODED
      )
    );
    try (
      var manager = PoolingHttpClientConnectionManagerBuilder.create()
        .setDefaultConnectionConfig(
          ConnectionConfig.custom()
            .setConnectTimeout(Timeout.ofSeconds(8))
            .setSocketTimeout(Timeout.ofSeconds(25))
            .build()
        )
        .build();
      var client = HttpClients.custom()
        .setConnectionManager(manager)
        .disableRedirectHandling()
        .disableAutomaticRetries()
        .setDefaultRequestConfig(
          RequestConfig.custom().setResponseTimeout(Timeout.ofSeconds(25)).build()
        )
        .build()
    ) {
      long deadline = System.nanoTime() + Duration.ofSeconds(45).toNanos();
      try (var deadlineTask = new br.com.wezttech.prospectgt.http.PrazoRequisicao(post, deadline)) {
        var response = client.executeOpen(null, post, null);
        try {
          if (response.getCode() != 200) throw new IOException(
            "Fonte externa respondeu HTTP " + response.getCode() + "."
          );
          if (response.getEntity() == null) throw new IOException(
            "Resposta vazia da fonte de empresas."
          );
          var stream = response.getEntity().getContent();
          var bytes = new ByteArrayOutputStream();
          byte[] block = new byte[8192];
          int n;
          while ((n = stream.read(block)) != -1) {
            if (System.nanoTime() > deadline) throw new IOException(
              "A fonte excedeu o tempo de leitura."
            );
            if (bytes.size() + n > 3_000_000) throw new IOException(
              "A fonte excedeu o limite seguro de resposta."
            );
            bytes.write(block, 0, n);
          }
          return interpretar(bytes.toByteArray(), category);
        } finally {
          post.cancel();
          try {
            response.close();
          } catch (IOException ignored) {
            /* Abortado; não drenar corpo. */
          }
        }
      }
    }
  }

  public List<Lugar> interpretar(byte[] bytes, String category) throws IOException {
    var root = mapper.readTree(bytes);
    if (root == null || root.has("remark")) throw new IOException(
      "A fonte devolveu uma pesquisa incompleta."
    );
    if (!root.has("elements") || !root.path("elements").isArray()) throw new IOException(
      "Resposta inesperada da fonte de empresas."
    );
    var out = new ArrayList<Lugar>();
    for (var e : root.path("elements")) {
      var tags = new HashMap<String, String>();
      e.path("tags")
        .fields()
        .forEachRemaining(x -> tags.put(x.getKey(), x.getValue().asText()));
      var coordinate = e.has("center") ? e.path("center") : e;
      if (!coordinate.has("lat") || !coordinate.has("lon")) continue;
      var p = Lugar.deTags(
        e.path("type").asText(),
        e.path("id").asLong(),
        tags,
        coordinate.path("lat").asDouble(),
        coordinate.path("lon").asDouble(),
        null,
        "OpenStreetMap / Overpass"
      );
      if (p != null && p.category().equals(category)) out.add(p);
    }
    return out;
  }
}
