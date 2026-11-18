package hu.aproleptek.api;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String,String>> badRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(DuplicateKeyException.class)
    ResponseEntity<Map<String,String>> duplicate(DuplicateKeyException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Már létezik ilyen azonosítójú tartalom."));
    }

    @ExceptionHandler(EmptyResultDataAccessException.class)
    ResponseEntity<Map<String,String>> notFound(EmptyResultDataAccessException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "A kért tartalom nem található."));
    }
}

