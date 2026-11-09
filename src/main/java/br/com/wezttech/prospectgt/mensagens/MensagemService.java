package br.com.wezttech.prospectgt.mensagens;

import br.com.wezttech.prospectgt.contas.Conta;
import br.com.wezttech.prospectgt.empresas.Empresa;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class MensagemService {

  private final com.fasterxml.jackson.databind.ObjectMapper mapper =
    new com.fasterxml.jackson.databind.ObjectMapper();

  public static String numero(String value) {
    if (value == null) return null;
    String first = value.split("[;|]")[0];
    String n = first.replaceAll("[^0-9]", "");
    if (n.length() == 10 || n.length() == 11) n = "55" + n;
    return n.length() >= 12 && n.length() <= 15 ? n : null;
  }

  public static String whatsappPublicado(String href) {
    try {
      URI u = URI.create(href);
      if ("wa.me".equalsIgnoreCase(u.getHost())) return numero(u.getPath());
      if (
        "api.whatsapp.com".equalsIgnoreCase(u.getHost()) ||
        "web.whatsapp.com".equalsIgnoreCase(u.getHost()) ||
        "whatsapp".equalsIgnoreCase(u.getScheme())
      ) {
        if (u.getRawQuery() != null) for (var p : u.getRawQuery().split("&"))
          if (p.startsWith("phone=")) return numero(
            URLDecoder.decode(p.substring(6), StandardCharsets.UTF_8)
          );
      }
    } catch (Exception ignored) {}
    return null;
  }

  public String preparar(Empresa e, Conta user) {
    String greet =
      e.contactName == null || e.contactName.isBlank()
        ? "Olá, equipe da " + e.name + "!"
        : "Olá, " + e.contactName + "!";
    String author = user.displayName.startsWith("Vendedor ")
      ? "Sou da equipe da WeztTech."
      : "Sou " + user.displayName + ", da WeztTech.";
    String context =
      "Vi o cadastro público da empresa e queria entender como vocês recebem novos contatos hoje.";
    if (e.website == null || e.website.isBlank()) {
      context =
        "O cadastro público que consultei não informa um site da empresa. Vocês já têm uma página própria?";
    } else if ("COMPLETED".equals(e.analysisState)) {
      try {
        var report = mapper.readTree(e.analysisDetails);
        if (
          report != null && report.has("contactFound") && !report.path("contactFound").asBoolean()
        ) context =
            "Na leitura automática da página do site, não encontrei um link ou formulário de contato no conteúdo consultado.";
      } catch (Exception ignored) {
        /* Ausência de evidência mantém a mensagem genérica. */
      }
    }
    String benefit = switch (Objects.toString(e.category, "")) {
      case "cafes", "restaurantes" -> "apresentar o cardápio e facilitar pedidos pelo WhatsApp";
      case "padarias" -> "apresentar os produtos e facilitar encomendas pelo WhatsApp";
      case
        "advocacia",
        "saude",
        "beleza",
        "academias" -> "apresentar os serviços e facilitar pedidos de agendamento pelo WhatsApp";
      case "hoteis" -> "apresentar as acomodações e facilitar pedidos de reserva pelo WhatsApp";
      case "oficinas" -> "mostrar os serviços e facilitar pedidos de orçamento pelo WhatsApp";
      default -> "destacar os produtos e facilitar pedidos e orçamentos pelo WhatsApp";
    };
    return (
      greet +
      " " +
      author +
      " " +
      context +
      " Uma landing page/site de vendas para a " +
      e.name +
      " pode ajudar a " +
      benefit +
      ". Podemos conversar para ver se faz sentido para vocês?"
    );
  }

  public String link(String phone, String text) {
    String number = numero(phone);
    if (number == null) throw new IllegalArgumentException("Contato sem um número utilizável.");
    return "https://wa.me/" + number + "?text=" + URLEncoder.encode(text, StandardCharsets.UTF_8);
  }
}
