package duocuc.cl.rodrigo.carniverocrud.service;

import duocuc.cl.rodrigo.carniverocrud.messaging.EventPublisher;
import duocuc.cl.rodrigo.carniverocrud.messaging.PedidoConfirmadoEvent;
import duocuc.cl.rodrigo.carniverocrud.messaging.RabbitConfig;
import duocuc.cl.rodrigo.carniverocrud.models.entity.Compra;
import duocuc.cl.rodrigo.carniverocrud.models.entity.Detalle_Compra;
import duocuc.cl.rodrigo.carniverocrud.models.entity.Planta;
import duocuc.cl.rodrigo.carniverocrud.models.entity.Usuario;
import duocuc.cl.rodrigo.carniverocrud.models.request.CheckoutRequest;
import duocuc.cl.rodrigo.carniverocrud.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronization;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class CheckoutService {

    @Autowired private CompraJpaRepository compraJpaRepository;
    @Autowired private DetalleCompraJpaRepository detalleCompraJpaRepository;
    @Autowired private PlantaJpaRepository plantaJpaRepository;
    @Autowired private UsuarioJpaRepository usuarioJpaRepository;
    @Autowired private EventPublisher eventPublisher;

    @Transactional
    public Compra procesarCheckout(String emailUsuarioAutenticado, CheckoutRequest request) {

        Usuario usuario = usuarioJpaRepository.findByEmail(emailUsuarioAutenticado)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new RuntimeException("El carrito está vacío.");
        }

        // 1. Crear la cabecera del pedido
        Compra compra = new Compra();
        compra.setIdUser(String.valueOf(usuario.getId()));
        compra.setEstado("PENDIENTE");
        compra = compraJpaRepository.save(compra);

        List<Detalle_Compra> detallesGuardados = new ArrayList<>();
        List<Planta> plantasCompradas = new ArrayList<>();
        int total = 0;

        // 2. Validar stock y crear cada línea
        for (CheckoutRequest.ItemCheckout item : request.getItems()) {
            Planta planta = plantaJpaRepository.findById(item.getIdPlanta())
                    .orElseThrow(() -> new RuntimeException("Planta no encontrada: " + item.getIdPlanta()));

            if (planta.getStock() < item.getCantidad()) {
                throw new RuntimeException("Stock insuficiente para: " + planta.getName());
            }

            planta.setStock(planta.getStock() - item.getCantidad());
            plantaJpaRepository.save(planta);

            Detalle_Compra detalle = new Detalle_Compra();
            detalle.setId_compra(compra.getId());
            detalle.setId_planta(planta.getId());
            detalle.setCantidad(item.getCantidad());
            detalle.setPrecio(planta.getPrice());
            detalleCompraJpaRepository.save(detalle);

            detallesGuardados.add(detalle);
            plantasCompradas.add(planta);
            total += planta.getPrice() * item.getCantidad();
        }

        // 3. Marcar como completado
        compra.setEstado("COMPLETADA");
        compra = compraJpaRepository.save(compra);

        // 4. Enviar correo de confirmación (si falla, no revierte la compra — se loguea aparte)
        // 4. Publicar el evento DESPUÉS del commit. Si la transacción falla, no sale ningún mensaje.
        PedidoConfirmadoEvent evento = construirEvento(usuario.getEmail(), compra,
        detallesGuardados, plantasCompradas, total);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
        eventPublisher.publicar(RabbitConfig.RK_PEDIDO_CONFIRMADO, evento);
    }
});
return compra;  
}
private PedidoConfirmadoEvent construirEvento(String email, Compra compra,List<Detalle_Compra> detalles, List<Planta> plantas, int total) {
    List<PedidoConfirmadoEvent.Item> items = new ArrayList<>();
    for (int i = 0; i < detalles.size(); i++) {
        Detalle_Compra d = detalles.get(i);
        items.add(new PedidoConfirmadoEvent.Item(
                plantas.get(i).getName(), d.getCantidad(), d.getCantidad() * d.getPrecio()));
    }
    return new PedidoConfirmadoEvent(
            compra.getId(), email,
            compra.getPurchasedate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")),
            compra.getEstado(), total, items);
}
}
