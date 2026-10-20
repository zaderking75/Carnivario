package duocuc.cl.rodrigo.carnivariorabbitadmin.exception;

import java.util.Map;

import duocuc.cl.rodrigo.carnivariorabbitadmin.service.RabbitAdminService.AdminOperationException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestControllerAdvice
public class RabbitAdminExceptionHandler {

    @ExceptionHandler(AdminOperationException.class)
    public ResponseEntity<Map<String, String>> administrationFailure(AdminOperationException exception) {
        return switch (exception.failure()) {
            case INVALID -> error(HttpStatus.BAD_REQUEST, "Solicitud inválida.");
            case FORBIDDEN -> error(HttpStatus.FORBIDDEN, "Operación no permitida sobre estos recursos.");
            case NOT_FOUND -> error(HttpStatus.NOT_FOUND, "Recurso no encontrado.");
            case CONFLICT -> error(HttpStatus.CONFLICT, "La configuración es incompatible con el recurso existente.");
            case UNAVAILABLE -> error(HttpStatus.SERVICE_UNAVAILABLE, "No se pudo completar la operación con RabbitMQ.");
        };
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            ConstraintViolationException.class, HandlerMethodValidationException.class})
    public ResponseEntity<Map<String, String>> invalidRequest(Exception exception) {
        return error(HttpStatus.BAD_REQUEST, "Solicitud inválida.");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, String>> methodNotAllowed() {
        return error(HttpStatus.METHOD_NOT_ALLOWED, "Método no permitido.");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, String>> unsupportedMediaType() {
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Tipo de contenido no admitido.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> unexpectedFailure(Exception exception) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo completar la solicitud.");
    }

    private ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("code", status.name(), "message", message));
    }
}
