package br.com.wezttech.prospectgt.web;

import br.com.wezttech.prospectgt.contas.*;
import java.security.Principal;
import org.springframework.stereotype.Component;

@Component
public class ContaAtual {

  private final ContaRepository contas;

  public ContaAtual(ContaRepository c) {
    contas = c;
  }

  public Conta get(Principal p) {
    if (p == null) throw new ApiException(
      org.springframework.http.HttpStatus.UNAUTHORIZED,
      "Entre na sua conta."
    );
    return contas.findByUsername(p.getName()).orElseThrow(ApiException::forbidden);
  }
}
