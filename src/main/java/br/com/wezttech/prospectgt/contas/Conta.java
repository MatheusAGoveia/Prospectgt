package br.com.wezttech.prospectgt.contas;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_user")
public class Conta {

  @Id
  public UUID id;

  @Column(nullable = false, unique = true)
  public String username;

  public String displayName;
  public String passwordHash;
  public String role;
  public String specialty = "";
  public boolean enabled = true;
  public Instant createdAt;

  public Conta() {}

  public Conta(String username, String name, String hash, String role, Instant now) {
    this.id = UUID.randomUUID();
    this.username = username;
    this.displayName = name;
    this.passwordHash = hash;
    this.role = role;
    this.createdAt = now;
  }
}
