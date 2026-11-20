package duocuc.cl.rodrigo.carniverocrud.restock;

import duocuc.cl.rodrigo.carniverocrud.models.entity.Planta;
import duocuc.cl.rodrigo.carniverocrud.models.entity.Usuario;
import duocuc.cl.rodrigo.carniverocrud.repository.PlantaJpaRepository;
import duocuc.cl.rodrigo.carniverocrud.repository.UsuarioJpaRepository;
import duocuc.cl.rodrigo.carniverocrud.restock.events.RestockEvent;
import duocuc.cl.rodrigo.carniverocrud.restock.producer.RestockEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RestockService {
    private final PlantaJpaRepository plantaRepository;
    private final UsuarioJpaRepository usuarioRepository;
    private final RestockEventPublisher publisher;

    public RestockService(PlantaJpaRepository plantaRepository,
                          UsuarioJpaRepository usuarioRepository,
                          RestockEventPublisher publisher) {
        this.plantaRepository = plantaRepository;
        this.usuarioRepository = usuarioRepository;
        this.publisher = publisher;
    }

    @Transactional
    public void solicitarAlerta(Integer plantaId, String emailAutenticado) {
        Planta planta = plantaRepository.findById(plantaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Planta no encontrada"));
        if (planta.getStock() > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La planta ya está disponible; no necesitas activar una alerta.");
        }
        Usuario usuario = usuarioRepository.findByEmail(emailAutenticado)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
        publisher.publicarSolicitud(RestockEvent.requested(
                usuario.getId(), usuario.getEmail(), planta.getId(), planta.getName(), planta.getPrice()));
    }

    @Transactional
    public void cancelarAlerta(Integer plantaId, String emailAutenticado) {
        Planta planta = plantaRepository.findById(plantaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Planta no encontrada"));
        Usuario usuario = usuarioRepository.findByEmail(emailAutenticado)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
        publisher.publicarCancelacion(RestockEvent.cancelled(usuario.getId(), planta.getId()));
    }
}
