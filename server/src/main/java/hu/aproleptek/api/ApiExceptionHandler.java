package hu.aproleptek.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException ex) {
        return response(HttpStatus.BAD_REQUEST, messageOr(ex.getMessage(), "Érvénytelen kérés."));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    ResponseEntity<Map<String, String>> malformedRequest(Exception ex) {
        if (ex instanceof MissingServletRequestParameterException missing) {
            return response(HttpStatus.BAD_REQUEST, "Hiányzó kötelező paraméter: " + missing.getParameterName() + ".");
        }
        if (ex instanceof MethodArgumentTypeMismatchException mismatch) {
            return response(HttpStatus.BAD_REQUEST, "Érvénytelen érték ehhez a mezőhöz: " + mismatch.getName() + ".");
        }
        return response(HttpStatus.BAD_REQUEST, "A kérés törzse hiányos vagy hibás formátumú.");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<Map<String, String>> unsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
        return response(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "A kérés fájlformátuma nem támogatott.");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<Map<String, String>> uploadTooLarge(MaxUploadSizeExceededException ex) {
        return response(HttpStatus.PAYLOAD_TOO_LARGE, "A feltöltés túllépi a megengedett méretet (25 MB kérésenként, legfeljebb 20 MB fájlonként).");
    }

    @ExceptionHandler(DuplicateKeyException.class)
    ResponseEntity<Map<String, String>> duplicate(DuplicateKeyException ex) {
        return response(HttpStatus.CONFLICT, "Már létezik ilyen azonosítójú tartalom.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Map<String, String>> invalidRelation(DataIntegrityViolationException ex) {
        return response(HttpStatus.CONFLICT, "A mentés ütközik egy meglévő kapcsolattal vagy adattal.");
    }

    @ExceptionHandler(EmptyResultDataAccessException.class)
    ResponseEntity<Map<String, String>> notFound(EmptyResultDataAccessException ex) {
        return response(HttpStatus.NOT_FOUND, "A kért tartalom nem található.");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, String>> unexpected(Exception ex) {
        log.error("Unhandled API exception", ex);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "Váratlan hiba történt. Próbáld újra később.");
    }

    private ResponseEntity<Map<String, String>> response(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message, "status", String.valueOf(status.value())));
    }

    private String messageOr(String message, String fallback) {
        return message == null || message.isBlank() ? fallback : message;
    }
}
