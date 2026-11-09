package br.com.wezttech.prospectgt.analise;

import java.io.IOException;
import java.net.*;
import java.util.*;

public final class PoliticaUrlPublica {

  private PoliticaUrlPublica() {}

  public static URI validar(String raw) throws IOException {
    try {
      if (raw == null || raw.isBlank()) throw new IOException("Site não informado.");
      String s = raw.strip();
      if (!s.contains("://")) s = "https://" + s;
      URI uri = URI.create(s);
      if (
        !Set.of("http", "https").contains(uri.getScheme().toLowerCase(Locale.ROOT)) ||
        uri.getHost() == null ||
        uri.getUserInfo() != null ||
        !(uri.getPort() == -1 || uri.getPort() == 80 || uri.getPort() == 443)
      ) throw new IOException(
        "Somente sites públicos HTTP/HTTPS, sem credenciais e em portas padrão."
      );
      String host = uri.getHost().toLowerCase(Locale.ROOT);
      if (host.indexOf(':') >= 0 || host.matches("[0-9.]+")) validarEndereco(
        InetAddress.getByName(host)
      );
      if (
        host.equals("localhost") ||
        host.endsWith(".localhost") ||
        host.endsWith(".local") ||
        host.endsWith(".internal") ||
        host.endsWith(".home.arpa")
      ) throw new IOException("Endereço interno não permitido.");
      return new URI(
        uri.getScheme(),
        null,
        uri.getHost(),
        uri.getPort(),
        uri.getPath().isEmpty() ? "/" : uri.getPath(),
        uri.getQuery(),
        null
      );
    } catch (URISyntaxException | IllegalArgumentException | NullPointerException ex) {
      throw new IOException("URL do site inválida.");
    }
  }

  public static void validarEndereco(InetAddress ip) throws IOException {
    byte[] b = ip.getAddress();
    if (
      ip.isAnyLocalAddress() ||
      ip.isLoopbackAddress() ||
      ip.isLinkLocalAddress() ||
      ip.isSiteLocalAddress() ||
      ip.isMulticastAddress()
    ) throw new IOException("Acesso a rede interna não permitido.");
    if (b.length == 4) {
      int a = b[0] & 255,
        c = b[1] & 255;
      if (
        a == 0 ||
        a >= 224 ||
        (a == 100 && c >= 64 && c <= 127) ||
        (a == 198 && (c == 18 || c == 19))
      ) throw new IOException("Faixa de endereço não permitida.");
    }
    if (b.length == 16 && (b[0] & 0xfe) == 0xfc) throw new IOException(
      "IPv6 privado não permitido."
    );
  }

  public static InetAddress[] resolver(String host) throws UnknownHostException {
    var ips = InetAddress.getAllByName(host);
    try {
      for (var ip : ips) validarEndereco(ip);
    } catch (IOException e) {
      throw new UnknownHostException(e.getMessage());
    }
    return ips;
  }
}
