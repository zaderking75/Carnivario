package duocuc.cl.rodrigo.carniverocrud.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.ConditionalRejectingErrorHandler;
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Conserva el converter y las propiedades de Boot; configura solo el rechazo del consumidor. */
@Configuration(proxyBeanMethods = false)
public class NotificationListenerConfig {
    private static final Logger log = LoggerFactory.getLogger(NotificationListenerConfig.class);

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer, ConnectionFactory connectionFactory) {
        var factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setErrorHandler(errorHandler());
        return factory;
    }

    static ConditionalRejectingErrorHandler errorHandler() {
        var strategy = new ConditionalRejectingErrorHandler.DefaultExceptionStrategy() {
            @Override
            protected void logFatalException(ListenerExecutionFailedException failure, Throwable cause) {
                log.warn("Conversión/argumentos irrecuperables tipo={}; rechazo sin requeue DLX={} DLQ={}",
                        cause.getClass().getSimpleName(), RabbitConfig.DLX, RabbitConfig.Q_CONFIRMACION_DLQ);
            }
        };
        var handler = new ConditionalRejectingErrorHandler(strategy) {
            @Override
            public void handleError(Throwable failure) {
                try {
                    super.handleError(failure);
                } catch (AmqpRejectAndDontRequeueException rejection) {
                    // El contenedor también registra esta excepción. No propagar el payload
                    // incluido en causas de Jackson ni repetir su stack trace.
                    var sanitized = new AmqpRejectAndDontRequeueException(
                            "Mensaje irrecuperable: rechazo sin requeue; consultar motivo saneado",
                            rejection.isRejectManual(), null);
                    sanitized.setStackTrace(new StackTraceElement[0]);
                    throw sanitized;
                }
            }

            @Override
            protected void log(Throwable failure) {
                // El handler estándar incluye el cuerpo y la excepción: no registrar datos personales.
                log.warn("Fallo del contenedor tipo={}", failure.getClass().getSimpleName());
            }
        };
        handler.setRejectManual(true);
        // Un mensaje inválido reprocesado debe volver a DLQ, no descartarse por tener x-death.
        handler.setDiscardFatalsWithXDeath(false);
        return handler;
    }
}
