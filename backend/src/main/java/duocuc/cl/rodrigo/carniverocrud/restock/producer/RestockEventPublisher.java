package duocuc.cl.rodrigo.carniverocrud.restock.producer;

import duocuc.cl.rodrigo.carniverocrud.restock.events.RestockEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class RestockEventPublisher {
    public static final String REQUESTED_TOPIC = "restock.requested";
    public static final String CANCELLED_TOPIC = "restock.cancelled";
    public static final String LISTED_TOPIC = "plant.listed";
    public static final String NOTIFICATION_TOPIC = "restock.notification.due";

    private static final Logger log = LoggerFactory.getLogger(RestockEventPublisher.class);
    private final KafkaTemplate<Object, Object> kafkaTemplate;

    public RestockEventPublisher(KafkaTemplate<Object, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publicarSolicitud(RestockEvent event) {
        publicarDespuesDelCommit(REQUESTED_TOPIC, event);
    }

    public void publicarCancelacion(RestockEvent event) {
        publicarDespuesDelCommit(CANCELLED_TOPIC, event);
    }

    public void publicarReposicion(RestockEvent event) {
        publicarDespuesDelCommit(LISTED_TOPIC, event);
    }

    private void publicarDespuesDelCommit(String topic, RestockEvent event) {
        Runnable envio = () -> kafkaTemplate
                .send(topic, String.valueOf(event.plantId()), event)
                .whenComplete((result, error) -> {
                    if (error != null) {
                        log.error("No se pudo publicar evento {} para planta {}", topic,
                                event.plantId(), error);
                    } else {
                        log.info("Evento {} publicado para planta {} en partición {}",
                                topic, event.plantId(), result.getRecordMetadata().partition());
                    }
                });

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
}
