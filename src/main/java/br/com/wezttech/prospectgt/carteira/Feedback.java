package br.com.wezttech.prospectgt.carteira;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "feedback")
public class Feedback {

  @Id
  public UUID id;

  public UUID leadId;
  public UUID actorId;
  public String state;
  public String comment;
  public Instant followUp;
  public Instant createdAt;
}
