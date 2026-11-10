package br.com.wezttech.prospectgt.operacao;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class ConexaoSegura {

  private ConexaoSegura() {}

  public static void validarJdbc(String value) {
    try {
      if (
        value == null || !value.startsWith("jdbc:postgresql://")
      ) throw new IllegalArgumentException();
      URI uri = URI.create(value.substring(5));
      if (
        uri.getHost() == null || uri.getUserInfo() != null || uri.getRawQuery() == null ||
        uri.getRawFragment() != null
      ) throw new IllegalArgumentException();
      var options = new HashMap<String, String>();
      for (String pair : uri.getRawQuery().split("&")) {
        String[] p = pair.split("=", 2);
        // pgjdbc não decodifica nomes de opções; recusar grafias ambíguas.
        if (p[0].contains("%") || p[0].contains("+")) throw new IllegalArgumentException();
        String key = p[0];
        String val = p.length < 2 ? "" : URLDecoder.decode(p[1], StandardCharsets.UTF_8);
        String lower = key.toLowerCase(Locale.ROOT);
        if (
          lower.startsWith("ssl") && !Set.of("sslmode", "sslfactory").contains(key)
        ) throw new IllegalArgumentException();
        if (
          lower.contains("password") || lower.contains("secret") || lower.contains("token")
        ) throw new IllegalArgumentException();
        if (
          options.put(key, val) != null ||
          Set.of("password", "user").contains(key.toLowerCase(Locale.ROOT))
        ) throw new IllegalArgumentException();
      }
      if (!"verify-full".equals(options.get("sslmode"))) throw new IllegalArgumentException();
      if (
        !"org.postgresql.ssl.DefaultJavaSSLFactory".equals(options.get("sslfactory"))
      ) throw new IllegalArgumentException();
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException(
        "A conexão cloud deve usar PostgreSQL com sslmode=verify-full e sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory, sem credenciais na URL."
      );
    }
  }
}
