package br.com.wezttech.prospectgt.contas;

import java.nio.file.*;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class BootstrapContas implements ApplicationRunner {

  private final ContaRepository contas;
  private final PasswordEncoder encoder;
  private final Clock clock;
  private final boolean enabled;
  private final String configuredPassword;

  public BootstrapContas(
    ContaRepository c,
    PasswordEncoder e,
    Clock clock,
    @Value("${prospectgt.bootstrap-enabled:true}") boolean enabled,
    @Value("${prospectgt.bootstrap-password:}") String password
  ) {
    this.contas = c;
    this.encoder = e;
    this.clock = clock;
    this.enabled = enabled;
    this.configuredPassword = password;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) throws Exception {
    if (!enabled || contas.count() > 0) return;
    if (
      !configuredPassword.isBlank() && configuredPassword.length() < 12
    ) throw new IllegalArgumentException(
      "BOOTSTRAP_PASSWORD precisa ter pelo menos 12 caracteres."
    );
    var access = new StringBuilder(
      "Prospectgt — acessos locais iniciais. Não publicar este arquivo.\n\n"
    );
    for (int i = 0; i <= 5; i++) {
      String username = i == 0 ? "admin" : "vendedor" + i;
      String password = i == 0 && !configuredPassword.isBlank() ? configuredPassword : password();
      contas.save(
        new Conta(
          username,
          i == 0 ? "Gestor WeztTech" : "Vendedor " + i,
          encoder.encode(password),
          i == 0 ? "ADMIN" : "SELLER",
          clock.instant()
        )
      );
      access.append(username).append(" : ").append(password).append("\n");
    }
    Path path = Path.of("data", "acessos-iniciais.txt");
    Files.createDirectories(path.getParent());
    Files.writeString(
      path,
      access.toString(),
      StandardOpenOption.CREATE,
      StandardOpenOption.TRUNCATE_EXISTING
    );
    System.out.println(
      "Acessos iniciais gravados em data/acessos-iniciais.txt (arquivo local, fora do Git)."
    );
  }

  private String password() {
    byte[] b = new byte[18];
    new SecureRandom().nextBytes(b);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
  }
}
