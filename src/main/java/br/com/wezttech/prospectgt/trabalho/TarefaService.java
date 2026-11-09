package br.com.wezttech.prospectgt.trabalho;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TarefaService {

  private final TarefaRepository jobs;
  private final Clock clock;

  public TarefaService(TarefaRepository r, Clock c) {
    jobs = r;
    clock = c;
  }

  @Transactional
  public Tarefa criar(String kind, UUID actor, UUID lead, String cache, String payload) {
    if (cache != null) {
      try {
        cache =
          kind +
          ":" +
          HexFormat.of().formatHex(
            java.security.MessageDigest.getInstance("SHA-256").digest(
              cache.getBytes(java.nio.charset.StandardCharsets.UTF_8)
            )
          );
      } catch (java.security.NoSuchAlgorithmException ex) {
        throw new IllegalStateException(ex);
      }
      for (String state : List.of("PENDING", "RUNNING")) {
        var existing = jobs.findFirstByCacheKeyAndStatusOrderByCreatedAtDesc(cache, state);
        if (
          existing.isPresent() && Objects.equals(actor, existing.get().actorId)
        ) return existing.get();
      }
      // Análises alteram a ficha; resultado de tarefa antigo não pode reaplicar evidência.
      var cached = kind.equals("ANALYZE")
        ? Optional.<Tarefa>empty()
        : jobs.findFirstByCacheKeyAndStatusOrderByCreatedAtDesc(cache, "COMPLETED");
      if (
        cached.isPresent() &&
        cached.get().createdAt.isAfter(clock.instant().minus(Duration.ofHours(24)))
      ) {
        var copy = new Tarefa();
        copy.id = UUID.randomUUID();
        copy.kind = kind;
        copy.actorId = actor;
        copy.leadId = lead;
        copy.cacheKey = null;
        copy.payload = payload;
        copy.status = "COMPLETED";
        copy.result = cached.get().result;
        copy.createdAt = clock.instant();
        copy.updatedAt = copy.createdAt;
        return jobs.save(copy);
      }
    }
    var j = new Tarefa();
    j.id = UUID.randomUUID();
    j.kind = kind;
    j.actorId = actor;
    j.leadId = lead;
    j.cacheKey = cache;
    j.payload = payload;
    j.status = "PENDING";
    j.createdAt = clock.instant();
    j.updatedAt = j.createdAt;
    return jobs.save(j);
  }

  @Transactional
  public Tarefa proxima() {
    var list = jobs.pendentes(org.springframework.data.domain.PageRequest.of(0, 1));
    if (list.isEmpty()) return null;
    var j = list.getFirst();
    j.status = "RUNNING";
    j.attempts++;
    j.updatedAt = clock.instant();
    return jobs.save(j);
  }

  @Transactional
  public void concluir(UUID id, String result) {
    var j = jobs.findById(id).orElseThrow();
    j.status = "COMPLETED";
    j.result = result;
    j.updatedAt = clock.instant();
    jobs.save(j);
  }

  @Transactional
  public void falhar(UUID id, String error) {
    var j = jobs.findById(id).orElseThrow();
    j.status = "FAILED";
    j.error =
      error == null
        ? "Não foi possível concluir a tarefa."
        : error.substring(0, Math.min(1800, error.length()));
    j.updatedAt = clock.instant();
    jobs.save(j);
  }

  @Transactional
  public void recuperar() {
    for (var j : jobs.findByStatus("RUNNING")) {
      j.status = j.attempts < 3 ? "PENDING" : "FAILED";
      j.error = null;
      j.updatedAt = clock.instant();
      jobs.save(j);
    }
  }
}
