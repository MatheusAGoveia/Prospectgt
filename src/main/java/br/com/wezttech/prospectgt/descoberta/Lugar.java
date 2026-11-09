package br.com.wezttech.prospectgt.descoberta;

import java.time.Instant;
import java.util.*;

public record Lugar(
  String key,
  String name,
  String category,
  String city,
  String address,
  double lat,
  double lon,
  String website,
  String phone,
  String whatsapp,
  String sourceUrl,
  String sourceName,
  Instant sourceUpdatedAt
) {
  public static Lugar deTags(
    String type,
    long id,
    Map<String, String> t,
    double lat,
    double lon,
    Instant updated,
    String source
  ) {
    String cat = Categorias.identificar(t),
      name = first(t, "name", "brand");
    if (cat == null || name == null) return null;
    String street = first(t, "addr:street"),
      num = first(t, "addr:housenumber"),
      city = first(t, "addr:city");
    String address = String.join(
      ", ",
      java.util.stream.Stream.of(street, num, first(t, "addr:suburb"), city)
        .filter(Objects::nonNull)
        .toList()
    );
    return new Lugar(
      "osm:" + type + ":" + id,
      name,
      cat,
      city,
      address.isBlank() ? null : address,
      lat,
      lon,
      first(t, "contact:website", "website"),
      first(t, "contact:phone", "phone", "contact:mobile", "mobile"),
      first(t, "contact:whatsapp", "whatsapp"),
      "https://www.openstreetmap.org/" + type + "/" + id,
      source,
      updated
    );
  }

  private static String first(Map<String, String> t, String... keys) {
    for (String k : keys) {
      String v = t.get(k);
      if (v != null && !v.isBlank()) return v.strip();
    }
    return null;
  }
}
