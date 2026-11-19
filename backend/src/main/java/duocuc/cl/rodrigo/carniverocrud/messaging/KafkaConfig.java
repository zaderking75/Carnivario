package duocuc.cl.rodrigo.carniverocrud.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfig {

    public static final String TOPIC_PEDIDOS = "carnivario.pedidos";
    public static final String TOPIC_PEDIDOS_DLT = "carnivario.pedidos.dlt";
    public static final String GROUP_AUDITORIA = "carnivario-auditoria";
    
 // Spring Boot crea estos tópicos al iniciar si todavía no existen.
    @Bean
    NewTopic pedidosTopic() {
        return TopicBuilder.name(TOPIC_PEDIDOS).partitions(3).replicas(1).build();
    }

    // Tópico de errores (DLT): equivalente a notificaciones.confirmacion.dlq de RabbitMQ.
    @Bean
    NewTopic pedidosDltTopic() {
        return TopicBuilder.name(TOPIC_PEDIDOS_DLT).partitions(3).replicas(1).build();
    }

    /**
     * 2 reintentos con 1 s de espera; si siguen fallando, el registro va al DLT.
     * Spring Boot aplica este handler a todos los @KafkaListener.
     */
    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaOperations<Object, Object> template) {
        var recoverer = new DeadLetterPublishingRecoverer(template,
                (record, ex) -> new TopicPartition(TOPIC_PEDIDOS_DLT, record.partition()));
        var handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 2L));
        // Un evento inválido no mejora reintentando: va directo al DLT.
        handler.addNotRetryableExceptions(IllegalArgumentException.class);
        return handler;
    }

}
