package br.com.wezttech.prospectgt.empresas;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "lead")
public class Empresa {

  @Id
  public UUID id;

  @Column(unique = true, nullable = false)
  public String sourceKey;

  public String name;
  public String category;
  public String city;
  public String address;
  public Double latitude;
  public Double longitude;
  public String website;
  public String phone;
  public String whatsapp;
  public String sourceWhatsapp;
  public String siteWhatsapp;
  public String contactName;
  public String sourceUrl;
  public String sourceName;
  public Instant sourceUpdatedAt;
  public Instant fetchedAt;
  public Instant updatedAt;
  public String state = "AVAILABLE";
  public UUID ownerId;
  public Instant reservedUntil;
  public Instant followUp;
  public int score;
  public String analysisState = "PENDING";

  @Column(columnDefinition = "text")
  public String analysisReasons = "[]";

  @Column(columnDefinition = "text")
  public String analysisDetails = "{}";

  public Instant analyzedAt;

  @Version
  public long version;
}
