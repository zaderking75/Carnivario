package duocuc.cl.rodrigo.carniverocrud.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class KafkaEventPublisher {
    
    private static final Logger log = LoggerFactory.getLogger(KafkaEventPublisher.class);
    private final KafkaTemplate<Object, Object> kafkaTemplate;

    public KafkaEventPublisher(KafkaTemplate<Object, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /** Publica el pedido en Kafka después del commit (igual que EventPublisher con RabbitMQ). */
    public void publicarPedido(PedidoConfirmadoEvent evento) {
        Runnable envio = () -> enviar(evento);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    envio.run();
                }
            });
        } else {
            envio.run();
        }
    }

    private void enviar(PedidoConfirmadoEvent evento) {
        try {
            // Clave = compraId: los eventos de una misma compra caen en la misma partición (orden garantizado).
            kafkaTemplate.send(KafkaConfig.TOPIC_PEDIDOS, String.valueOf(evento.compraId()), evento)
                    .whenComplete((res, ex) -> {
                        if (ex != null) {
                            log.error("Kafka: no se pudo publicar compraId={}: {}", evento.compraId(), ex.getMessage());
                        } else {
                            log.info("Kafka: compraId={} estado={} -> partición={} offset={}",
                                    evento.compraId(), evento.estado(),
                                    res.getRecordMetadata().partition(), res.getRecordMetadata().offset());
                        }
                    });
        } catch (RuntimeException ex) {
            // La compra ya está confirmada: si Kafka no responde, no se revierte nada.
            log.error("Kafka: error al enviar compraId={}: {}", evento.compraId(), ex.getMessage());
        }
    }

}
