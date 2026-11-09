package br.com.wezttech.prospectgt.empresas;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface EmpresaRepository extends JpaRepository<Empresa, UUID> {
  Optional<Empresa> findBySourceKey(String key);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select e from Empresa e where e.id=:id")
  Optional<Empresa> locked(@Param("id") UUID id);

  List<Empresa> findTop300ByOrderByUpdatedAtDesc();
  List<Empresa> findByOwnerIdOrderByUpdatedAtDesc(UUID owner);
  List<Empresa> findByReservedUntilBeforeAndStateIn(
    java.time.Instant now,
    java.util.Collection<String> states
  );
}
