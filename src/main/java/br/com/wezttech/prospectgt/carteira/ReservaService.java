package br.com.wezttech.prospectgt.carteira;

import br.com.wezttech.prospectgt.auditoria.*;
import br.com.wezttech.prospectgt.contas.*;
import br.com.wezttech.prospectgt.empresas.*;
import br.com.wezttech.prospectgt.web.ApiException;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservaService {

  private final EmpresaRepository empresas;
  private final ContaRepository contas;
  private final AuditoriaService audit;
  private final FeedbackRepository feedback;
  private final Clock clock;

  public ReservaService(
    EmpresaRepository e,
    ContaRepository c,
    AuditoriaService a,
    FeedbackRepository f,
    Clock clock
  ) {
    empresas = e;
    contas = c;
    audit = a;
    feedback = f;
    this.clock = clock;
  }

  @Transactional
  public Empresa assumir(UUID id, Conta user) {
    Empresa e = empresas.locked(id).orElseThrow(ApiException::missing);
    expire(e);
    if (
      List.of("WON", "DO_NOT_CONTACT", "NOT_INTERESTED").contains(e.state)
    ) throw ApiException.conflict("Esta empresa está fora da fila de novas abordagens.");
    if (e.ownerId != null && !e.ownerId.equals(user.id)) {
      String name = contas
        .findById(e.ownerId)
        .map(x -> x.displayName)
        .orElse("outro vendedor");
      throw ApiException.conflict("Oportunidade reservada para " + name + ".");
    }
    if (e.ownerId == null) {
      e.ownerId = user.id;
      e.state = "IN_PROGRESS";
      e.reservedUntil = DiasUteis.somar(clock.instant(), 3);
      e.updatedAt = clock.instant();
      audit.registrar(id, user.id, "CLAIMED", "Reserva de três dias úteis.");
    }
    return empresas.save(e);
  }

  @Transactional
  public void registrar(UUID id, Conta user, String state, String comment, LocalDate followUp) {
    if (
      state == null ||
      !Set.of("IN_PROGRESS", "FOLLOW_UP", "WON", "NOT_INTERESTED", "DO_NOT_CONTACT").contains(state)
    ) throw ApiException.bad("Situação inválida.");
    if (comment == null || comment.isBlank() || comment.length() > 2000) throw ApiException.bad(
      "Descreva o resultado da abordagem (até 2.000 caracteres)."
    );
    Empresa e = empresas.locked(id).orElseThrow(ApiException::missing);
    expire(e);
    if (!user.id.equals(e.ownerId) && !user.role.equals("ADMIN")) throw ApiException.forbidden();
    if (
      Set.of("WON", "NOT_INTERESTED", "DO_NOT_CONTACT").contains(e.state) && !e.state.equals(state)
    ) {
      throw ApiException.conflict(
        "Somente a gestão pode reabrir uma oportunidade encerrada, informando o motivo."
      );
    }
    if (
      followUp != null && followUp.isBefore(LocalDate.now(clock.withZone(DiasUteis.ZONE)))
    ) throw ApiException.bad("O retorno não pode ficar no passado.");
    if (state.equals("FOLLOW_UP") && followUp == null) throw ApiException.bad(
      "Informe a data do próximo contato."
    );
    if (e.ownerId == null && Set.of("IN_PROGRESS", "FOLLOW_UP").contains(state)) {
      e.ownerId = user.id;
      audit.registrar(
        id,
        user.id,
        "CLAIMED",
        "Gestor assumiu a oportunidade ao registrar o atendimento."
      );
    }
    e.state = state;
    e.followUp = followUp == null ? null : followUp.atStartOfDay(DiasUteis.ZONE).toInstant();
    e.reservedUntil = Set.of("WON", "NOT_INTERESTED", "DO_NOT_CONTACT").contains(state)
      ? null
      : followUp == null
        ? DiasUteis.somar(clock.instant(), 3)
        : DiasUteis.depoisDoRetorno(followUp);
    e.updatedAt = clock.instant();
    empresas.save(e);
    var f = new Feedback();
    f.id = UUID.randomUUID();
    f.leadId = id;
    f.actorId = user.id;
    f.state = state;
    f.comment = comment.strip();
    f.followUp = e.followUp;
    f.createdAt = clock.instant();
    feedback.save(f);
    audit.registrar(id, user.id, "FEEDBACK", comment.strip());
  }

  @Transactional
  public void liberar(UUID id, Conta admin, String reason) {
    if (!admin.role.equals("ADMIN")) throw ApiException.forbidden();
    if (reason == null || reason.isBlank() || reason.length() > 2000) throw ApiException.bad(
      "Informe o motivo da reabertura (até 2.000 caracteres)."
    );
    var e = empresas.locked(id).orElseThrow(ApiException::missing);
    e.ownerId = null;
    e.reservedUntil = null;
    e.followUp = null;
    e.state = "AVAILABLE";
    e.updatedAt = clock.instant();
    empresas.save(e);
    audit.registrar(id, admin.id, "ADMIN_RELEASED", "Reabertura: " + reason.strip());
  }

  public void expire(Empresa e) {
    if (
      e.reservedUntil != null &&
      !e.reservedUntil.isAfter(clock.instant()) &&
      Set.of("IN_PROGRESS", "FOLLOW_UP").contains(e.state)
    ) {
      e.ownerId = null;
      e.reservedUntil = null;
      e.followUp = null;
      e.state = "AVAILABLE";
      e.updatedAt = clock.instant();
      audit.registrar(e.id, null, "RESERVATION_EXPIRED", "Reserva liberada por expiração.");
    }
  }

  @Transactional
  public void expirarTodas() {
    for (var summary : empresas.findByReservedUntilBeforeAndStateIn(
      clock.instant(),
      Set.of("IN_PROGRESS", "FOLLOW_UP")
    )) {
      var e = empresas.locked(summary.id).orElseThrow();
      expire(e);
      empresas.save(e);
    }
  }
}
