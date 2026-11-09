package br.com.wezttech.prospectgt.trabalho;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "work_job")
public class Tarefa {

  @Id
  public UUID id;

  public String kind;
  public String status;
  public UUID actorId;
  public UUID leadId;
  public String cacheKey;

  @Column(columnDefinition = "text")
  public String payload;

  @Column(columnDefinition = "text")
  public String result;

  public String error;
  public int attempts;
  public Instant createdAt;
  public Instant updatedAt;
}
