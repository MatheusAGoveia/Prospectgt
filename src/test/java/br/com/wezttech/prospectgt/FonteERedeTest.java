package br.com.wezttech.prospectgt;

import static org.assertj.core.api.Assertions.*;

import br.com.wezttech.prospectgt.descoberta.FonteOverpass;
import br.com.wezttech.prospectgt.http.PrazoRequisicao;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.junit.jupiter.api.Test;

class FonteERedeTest {

  @Test
  void redirecionamentoNaoDrenaCorpoNemConverteSucessoEmSocketFechado() throws Exception {
    try (var server = new ServerSocket(0, 2, InetAddress.getLoopbackAddress())) {
      String base = "http://127.0.0.1:" + server.getLocalPort();
      Thread peer = Thread.ofVirtual().start(() -> {
        try {
          for (int i = 0; i < 2; i++) try (var socket = server.accept()) {
            socket.setSoTimeout(3000);
            var requestHeaders = new BufferedReader(
              new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII)
            );
            String header;
            while ((header = requestHeaders.readLine()) != null && !header.isEmpty()) {}
            var out = socket.getOutputStream();
            String reply =
              i == 0
                ? "HTTP/1.1 302 Found\r\nConnection: close\r\nLocation: /final\r\nContent-Type: text/html\r\nContent-Length: 1000000\r\n\r\nx"
                : "HTTP/1.1 200 OK\r\nConnection: close\r\nContent-Type: text/html\r\nContent-Length: 14\r\n\r\n<h1>Teste</h1>";
            out.write(reply.getBytes(StandardCharsets.UTF_8));
            out.flush();
          }
        } catch (Exception ignored) {}
      });
      // Apenas o transporte é testado contra o fixture local. A política real de
      // URLs privadas permanece ativa em produção e tem testes próprios.
      try (
        var policy = org.mockito.Mockito.mockStatic(
          br.com.wezttech.prospectgt.analise.PoliticaUrlPublica.class,
          invocation -> {
            String method = invocation.getMethod().getName();
            if (
              method.equals("validar") &&
              invocation
                .getArgument(0)
                .toString()
                .startsWith(base + "/")
            ) return URI.create(invocation.getArgument(0));
            if (
              method.equals("resolver") && invocation.getArgument(0).equals("127.0.0.1")
            ) return new InetAddress[] { InetAddress.getLoopbackAddress() };
            return invocation.callRealMethod();
          }
        )
      ) {
        var result = new br.com.wezttech.prospectgt.analise.LeitorSitePublico().ler(
          base + "/first"
        );
        assertThat(result.code()).isEqualTo(200);
        assertThat(result.url()).endsWith("/final");
        assertThat(result.html()).contains("Teste");
      } finally {
        peer.interrupt();
        peer.join(1000);
      }
    }
  }

  @Test
  void respostaParcialDaFonteNaoViraBuscaVazia() {
    var fonte = new FonteOverpass("https://example.com", new ObjectMapper());
    assertThatThrownBy(() ->
      fonte.interpretar(
        "{\"remark\":\"runtime timeout\",\"elements\":[]}".getBytes(StandardCharsets.UTF_8),
        "cafes"
      )
    ).isInstanceOf(IOException.class);
    assertThatThrownBy(() ->
      fonte.interpretar("{}".getBytes(StandardCharsets.UTF_8), "cafes")
    ).isInstanceOf(IOException.class);
  }

  @Test
  void deadlineInterrompeCabecalhoQueNuncaTermina() throws Exception {
    try (
      var server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
      var client = HttpClients.custom().disableAutomaticRetries().build()
    ) {
      Thread peer = Thread.ofVirtual().start(() -> {
        try (var socket = server.accept()) {
          var out = socket.getOutputStream();
          out.write("HTTP/1.1 200 OK\r\nX-Slow: ".getBytes(StandardCharsets.US_ASCII));
          out.flush();
          for (int i = 0; i < 300; i++) {
            out.write('x');
            out.flush();
            Thread.sleep(20);
          }
        } catch (Exception ignored) {}
      });
      var request = new HttpGet("http://127.0.0.1:" + server.getLocalPort() + "/");
      long start = System.nanoTime();
      try (var deadline = new PrazoRequisicao(request, start + Duration.ofMillis(400).toNanos())) {
        assertThatThrownBy(() -> client.executeOpen(null, request, null)).isInstanceOf(
          IOException.class
        );
      }
      assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(3));
      peer.interrupt();
      peer.join(1000);
    }
  }
}
