package br.com.wezttech.prospectgt.auditoria;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventoRepository extends JpaRepository<Evento, UUID> {
  List<Evento> findTop80ByLeadIdOrderByCreatedAtDesc(UUID leadId);
}
