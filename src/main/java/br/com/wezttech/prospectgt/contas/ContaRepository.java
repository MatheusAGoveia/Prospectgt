package br.com.wezttech.prospectgt.contas;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContaRepository extends JpaRepository<Conta, UUID> {
  Optional<Conta> findByUsername(String username);
}
