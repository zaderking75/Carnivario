package duocuc.cl.rodrigo.carniverocrud.messaging;

import org.springframework.amqp.core.*;
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

    // A1: confirmación de pedido
    public static final String RK_PEDIDO_CONFIRMADO = "pedido.confirmado";
    public static final String Q_CONFIRMACION = "notificaciones.confirmacion.queue";
    public static final String Q_CONFIRMACION_DLQ = "notificaciones.confirmacion.dlq";

    @Bean
    public TopicExchange eventosExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DLX, true, false);
    }

    @Bean
    public Queue confirmacionQueue() {
        return QueueBuilder.durable(Q_CONFIRMACION)
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", Q_CONFIRMACION_DLQ)
                .build();
    }

    @Bean
    public Queue confirmacionDlq() {
        return QueueBuilder.durable(Q_CONFIRMACION_DLQ).build();
    }

    @Bean
    public Binding confirmacionBinding() {
        return BindingBuilder.bind(confirmacionQueue()).to(eventosExchange()).with(RK_PEDIDO_CONFIRMADO);
    }

    @Bean
    public Binding confirmacionDlqBinding() {
        return BindingBuilder.bind(confirmacionDlq()).to(deadLetterExchange()).with(Q_CONFIRMACION_DLQ);
    }

    // Mensajes en JSON (legibles en la UI de RabbitMQ). El consumidor deduce el tipo desde su
    // parámetro, así no depende del nombre de clase del publicador.
    @Bean
    public MessageConverter jsonMessageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTypePrecedence(Jackson2JavaTypeMapper.TypePrecedence.INFERRED);
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }
}