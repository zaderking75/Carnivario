package duocuc.cl.rodrigo.carniverocrud.messaging;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import duocuc.cl.rodrigo.carniverocrud.models.entity.Compra;
import duocuc.cl.rodrigo.carniverocrud.models.entity.Detalle_Compra;
import duocuc.cl.rodrigo.carniverocrud.models.entity.Planta;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.List;

@Component
public class EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);
    private final RabbitTemplate rabbitTemplate;

    public EventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publicar(String routingKey, Object evento) {
        try {
            rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, routingKey, evento);
        } catch (AmqpException e) {
            // La compra ya está confirmada: si RabbitMQ no responde, no se revierte nada.
            log.error("No se pudo publicar '{}': {}", routingKey, e.getMessage());
        }
    }
    public void publicarPedidoConfirmado(String emailCliente, Compra compra,
                                     List<Detalle_Compra> detalles, List<Planta> plantas, int total) {
        publicarPedido(RabbitConfig.RK_PEDIDO_CONFIRMADO, emailCliente, compra, detalles, plantas, total);
    }

    public void publicarPedidoPendiente(String emailCliente, Compra compra,
                                       List<Detalle_Compra> detalles, List<Planta> plantas, int total) {
        publicarPedido(RabbitConfig.RK_PEDIDO_PENDIENTE, emailCliente, compra, detalles, plantas, total);
    }

    private void publicarPedido(String routingKey, String emailCliente, Compra compra,
                                List<Detalle_Compra> detalles, List<Planta> plantas, int total) {
        try {
            List<PedidoConfirmadoEvent.Item> items = new ArrayList<>();
            for (int i = 0; i < detalles.size(); i++) {
                Detalle_Compra d = detalles.get(i);
                items.add(new PedidoConfirmadoEvent.Item(
                        plantas.get(i).getName(), d.getCantidad(), d.getCantidad() * d.getPrecio()));
            }

            PedidoConfirmadoEvent evento = new PedidoConfirmadoEvent(
                    compra.getId(),
                    emailCliente,
                    compra.getPurchasedate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")),
                    compra.getEstado(),
                    total,
                    items);

            publicarTrasCommit(routingKey, evento);
        } catch (Exception e) {
            // Un problema de mensajería nunca debe revertir una compra ya válida.
            log.error("No se pudo preparar el evento de pedido '{}': {}", routingKey, e.getMessage());
        }
    }

    private void publicarTrasCommit(String routingKey, Object evento) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publicar(routingKey, evento);
            }
        });
    } else {
        publicar(routingKey, evento);
    }
    }
}
