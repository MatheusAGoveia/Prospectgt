package br.com.wezttech.prospectgt.descoberta;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RegioesService {

  public record Cidade(String label, double lat, double lon) {}

  private final List<Cidade> cities;

  public RegioesService(ObjectMapper mapper) throws IOException {
    try (var input = RegioesService.class.getResourceAsStream("/catalog-regions.json")) {
      if (input == null) throw new IOException("Configuração de regiões ausente.");
      cities = List.of(mapper.treeToValue(mapper.readTree(input).path("cities"), Cidade[].class));
    }
    if (cities.isEmpty()) throw new IOException("Nenhuma cidade configurada.");
  }

  public List<Cidade> cidades() {
    return cities;
  }
}
