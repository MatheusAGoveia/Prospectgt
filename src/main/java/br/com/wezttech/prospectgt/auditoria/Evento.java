package br.com.wezttech.prospectgt.auditoria;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_event")
public class Evento {

  @Id
  public UUID id;

  public UUID leadId;
  public UUID actorId;
  public String action;
  public String detail;
  public Instant createdAt;

  public Evento() {}

  public Evento(UUID lead, UUID actor, String action, String detail, Instant now) {
    this.id = UUID.randomUUID();
    this.leadId = lead;
    this.actorId = actor;
    this.action = action;
    this.detail = detail;
    this.createdAt = now;
  }
}
