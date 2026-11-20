package duocuc.cl.rodrigo.carniverocrud.restock;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/planta/api/{id}/alerta")
public class RestockController {
    private final RestockService restockService;

    public RestockController(RestockService restockService) {
        this.restockService = restockService;
    }

    @PostMapping
    public ResponseEntity<?> solicitar(@PathVariable Integer id, Authentication authentication) {
        restockService.solicitarAlerta(id, authentication.getName());
        return ResponseEntity.accepted().body(Map.of(
                "mensaje", "Registramos tu solicitud. Te avisaremos cuando vuelva a estar disponible."));
    }

    @DeleteMapping
    public ResponseEntity<?> cancelar(@PathVariable Integer id, Authentication authentication) {
        restockService.cancelarAlerta(id, authentication.getName());
        return ResponseEntity.ok(Map.of("mensaje", "Solicitud de alerta cancelada."));
    }
}
