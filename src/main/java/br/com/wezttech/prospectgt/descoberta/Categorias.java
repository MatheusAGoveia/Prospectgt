package br.com.wezttech.prospectgt.descoberta;

import java.util.*;

public final class Categorias {

  public static final Map<String, String> LABELS;

  static {
    var m = new LinkedHashMap<String, String>();
    m.put("cafes", "Cafés");
    m.put("padarias", "Padarias e confeitarias");
    m.put("restaurantes", "Restaurantes");
    m.put("advocacia", "Escritórios de advocacia");
    m.put("beleza", "Beleza e estética");
    m.put("saude", "Clínicas e odontologia");
    m.put("lojas", "Lojas");
    m.put("oficinas", "Oficinas");
    m.put("hoteis", "Hospedagem");
    m.put("academias", "Academias");
    LABELS = Collections.unmodifiableMap(m);
  }

  private Categorias() {}

  public static String identificar(Map<String, String> t) {
    String amenity = t.getOrDefault("amenity", ""),
      shop = t.getOrDefault("shop", ""),
      office = t.getOrDefault("office", "");
    if (amenity.equals("cafe")) return "cafes";
    if (Set.of("bakery", "confectionery", "pastry").contains(shop)) return "padarias";
    if (Set.of("restaurant", "fast_food", "food_court").contains(amenity)) return "restaurantes";
    if (office.equals("lawyer")) return "advocacia";
    if (Set.of("beauty", "hairdresser", "cosmetics", "massage").contains(shop)) return "beleza";
    if (
      Set.of("doctors", "dentist", "clinic").contains(amenity) ||
      Set.of("clinic", "dentist", "doctor").contains(t.getOrDefault("healthcare", ""))
    ) return "saude";
    if (shop.equals("car_repair")) return "oficinas";
    if (
      Set.of("hotel", "guest_house", "hostel", "motel").contains(t.getOrDefault("tourism", ""))
    ) return "hoteis";
    if (
      Set.of("fitness_centre", "sports_centre").contains(t.getOrDefault("leisure", ""))
    ) return "academias";
    if (!shop.isBlank() && !Set.of("vacant", "no", "yes").contains(shop)) return "lojas";
    return null;
  }

  public static String query(String c) {
    return switch (c) {
      case "cafes" -> "[amenity=cafe]";
      case "padarias" -> "[shop~\"^(bakery|confectionery|pastry)$\"]";
      case "restaurantes" -> "[amenity~\"^(restaurant|fast_food)$\"]";
      case "advocacia" -> "[office=lawyer]";
      case "beleza" -> "[shop~\"^(beauty|hairdresser|cosmetics)$\"]";
      case "saude" -> "[amenity~\"^(doctors|dentist|clinic)$\"]";
      case "lojas" -> "[shop]";
      case "oficinas" -> "[shop=car_repair]";
      case "hoteis" -> "[tourism~\"^(hotel|guest_house|hostel)$\"]";
      case "academias" -> "[leisure=fitness_centre]";
      default -> throw new IllegalArgumentException("Categoria inválida");
    };
  }
}
