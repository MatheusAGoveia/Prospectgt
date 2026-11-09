package br.com.wezttech.prospectgt.web;

import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class ApiErrors {

  @ExceptionHandler(ApiException.class)
  ResponseEntity<?> handle(ApiException e) {
    return ResponseEntity.status(e.status).body(Map.of("message", e.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<?> invalid(MethodArgumentNotValidException e) {
    return ResponseEntity.badRequest().body(Map.of("message", "Confira os campos informados."));
  }

  @ExceptionHandler(org.springframework.dao.OptimisticLockingFailureException.class)
  ResponseEntity<?> changed(Exception e) {
    return ResponseEntity.status(409).body(
      Map.of("message", "O registro mudou. Atualize a página e tente novamente.")
    );
  }
}
