package br.com.wezttech.prospectgt.analise;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.apache.hc.client5.http.*;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.*;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.stereotype.Service;

@Service
public class LeitorSitePublico {

  public record Page(int code, String url, String html, String location, String type) {}

  public Page ler(String raw) throws IOException {
    URI current = PoliticaUrlPublica.validar(raw);
    var dns = new DnsResolver() {
      public InetAddress[] resolve(String host) throws UnknownHostException {
        return PoliticaUrlPublica.resolver(host);
      }

      public String resolveCanonicalHostname(String host) throws UnknownHostException {
        resolve(host);
        return host;
      }
    };
    try (
      var manager = PoolingHttpClientConnectionManagerBuilder.create()
        .setDnsResolver(dns)
        .setDefaultConnectionConfig(
          ConnectionConfig.custom()
            .setConnectTimeout(Timeout.ofSeconds(5))
            .setSocketTimeout(Timeout.ofSeconds(6))
            .build()
        )
        .build();
      var client = HttpClients.custom()
        .setConnectionManager(manager)
        .disableRedirectHandling()
        .disableAutomaticRetries()
        .setDefaultRequestConfig(
          RequestConfig.custom().setResponseTimeout(Timeout.ofSeconds(8)).build()
        )
        .build()
    ) {
      long deadline = System.nanoTime() + java.time.Duration.ofSeconds(25).toNanos();
      for (int hop = 0; hop < 4; hop++) {
        if (System.nanoTime() > deadline) throw new IOException("Tempo total de análise excedido.");
        var get = new HttpGet(current);
        get.setHeader("User-Agent", "Prospectgt/0.1 (single public website audit)");
        final String requested = current.toString();
        try (
          var deadlineTask = new br.com.wezttech.prospectgt.http.PrazoRequisicao(get, deadline)
        ) {
          var response = client.executeOpen(null, get, null);
          Page page;
          try {
            var entity = response.getEntity();
            String type =
              response.getFirstHeader("Content-Type") == null
                ? ""
                : response.getFirstHeader("Content-Type").getValue();
            String location =
              response.getFirstHeader("Location") == null
                ? null
                : response.getFirstHeader("Location").getValue();
            if (response.getCode() >= 300 && response.getCode() < 400) {
              page = new Page(response.getCode(), requested, "", location, type);
            } else {
              if (
                !type.toLowerCase().contains("text/html") &&
                !type.toLowerCase().contains("application/xhtml")
              ) throw new IOException("O endereço não devolveu uma página HTML.");
              var out = new ByteArrayOutputStream();
              if (entity != null) {
                var input = entity.getContent();
                byte[] buffer = new byte[8192];
                int n;
                while ((n = input.read(buffer)) != -1) {
                  if (System.nanoTime() > deadline) throw new IOException(
                    "Tempo total de análise excedido."
                  );
                  if (out.size() + n > 2_097_152) throw new IOException(
                    "Página excede o limite de leitura de 2 MB."
                  );
                  out.write(buffer, 0, n);
                }
              }
              page = new Page(
                response.getCode(),
                requested,
                out.toString(StandardCharsets.UTF_8),
                location,
                type
              );
            }
          } finally {
            // Abort before closing: do not drain oversized or redirect response bodies.
            get.cancel();
            try {
              response.close();
            } catch (IOException ignored) {
              /* Abortado; não drenar corpo. */
            }
          }
          if (page.code >= 300 && page.code < 400) {
            if (page.location == null) throw new IOException("Redirecionamento sem destino.");
            current = PoliticaUrlPublica.validar(current.resolve(page.location).toString());
            continue;
          }
          return page;
        }
      }
    }
    throw new IOException("Excesso de redirecionamentos.");
  }
}
