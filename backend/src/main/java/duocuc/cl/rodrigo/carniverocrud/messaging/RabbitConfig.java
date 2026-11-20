
package duocuc.cl.rodrigo.carniverocrud.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "carnivario.eventos";
    public static final String DLX = "carnivario.dlx";

    public static final String RK_PEDIDO_CONFIRMADO = "pedido.confirmado";
    public static final String RK_PEDIDO_PENDIENTE = "pedido.pendiente";

    public static final String Q_CONFIRMACION =
            "notificaciones.confirmacion.queue";

    public static final String Q_CONFIRMACION_DLQ =
            "notificaciones.confirmacion.dlq";

    // Exchange principal
    @Bean
    public DirectExchange eventosExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    // Exchange de mensajes rechazados
    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DLX, true, false);
    }

    // Cola principal con configuración de Dead Letter Exchange
    @Bean
    public Queue colaConfirmacion() {
        return QueueBuilder.durable(Q_CONFIRMACION)
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", Q_CONFIRMACION_DLQ)
                .build();
    }

    // Cola de mensajes rechazados
    @Bean
    public Queue colaConfirmacionDLQ() {
        return QueueBuilder.durable(Q_CONFIRMACION_DLQ).build();
    }

    // Binding para pedidos confirmados
    @Bean
    public Binding bindingPedidoConfirmado() {
        return BindingBuilder
                .bind(colaConfirmacion())
                .to(eventosExchange())
                .with(RK_PEDIDO_CONFIRMADO);
    }

    // Binding para pedidos pendientes
    @Bean
    public Binding bindingPedidoPendiente() {
        return BindingBuilder
                .bind(colaConfirmacion())
                .to(eventosExchange())
                .with(RK_PEDIDO_PENDIENTE);
    }

    // Binding de la Dead Letter Queue
    @Bean
    public Binding bindingDeadLetter() {
        return BindingBuilder
                .bind(colaConfirmacionDLQ())
                .to(deadLetterExchange())
                .with(Q_CONFIRMACION_DLQ);
    }

    // Conversor JSON
    @Bean
    public MessageConverter jsonMessageConverter() {
        Jackson2JsonMessageConverter converter =
                new Jackson2JsonMessageConverter();

        DefaultJackson2JavaTypeMapper typeMapper =
                new DefaultJackson2JavaTypeMapper();

        typeMapper.setTypePrecedence(
                Jackson2JavaTypeMapper.TypePrecedence.INFERRED
        );

        converter.setJavaTypeMapper(typeMapper);

        return converter;
    }
}
