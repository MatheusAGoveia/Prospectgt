package br.com.wezttech.prospectgt.auditoria;

import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AuditoriaService {

  private final EventoRepository repository;
  private final Clock clock;

  public AuditoriaService(EventoRepository r, Clock c) {
    repository = r;
    clock = c;
  }

  public void registrar(UUID empresa, UUID ator, String acao, String detalhe) {
    repository.save(new Evento(empresa, ator, acao, detalhe, clock.instant()));
  }
}
