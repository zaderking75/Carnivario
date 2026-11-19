package duocuc.cl.rodrigo.carniverocrud.messaging;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumidor Kafka: lleva el historial/auditoría de pedidos.
 * No envía correos (eso lo sigue haciendo NotificacionesListener vía RabbitMQ),
 * así el cliente no recibe correos duplicados.
 */

@Component
public class AuditoriaPedidosListener {
     private static final Logger log = LoggerFactory.getLogger(AuditoriaPedidosListener.class);

    @KafkaListener(topics = KafkaConfig.TOPIC_PEDIDOS, groupId = KafkaConfig.GROUP_AUDITORIA)
    public void onPedido(ConsumerRecord<String, PedidoConfirmadoEvent> record) {
        PedidoConfirmadoEvent evento = record.value();
        if (evento == null || evento.compraId() == null || evento.compraId() <= 0) {
            throw new IllegalArgumentException("evento de pedido inválido"); // no se reintenta: va al DLT
        }
        log.info("Auditoría compraId={} estado={} total={} items={} partición={} offset={}",
                evento.compraId(), evento.estado(), evento.total(),
                evento.items() == null ? 0 : evento.items().size(),
                record.partition(), record.offset());
        // Aquí puedes guardar el evento en una tabla de historial, actualizar métricas, etc.
    }
}
