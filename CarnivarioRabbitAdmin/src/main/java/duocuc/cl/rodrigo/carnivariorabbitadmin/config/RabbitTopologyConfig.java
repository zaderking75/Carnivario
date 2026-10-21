package duocuc.cl.rodrigo.carnivariorabbitadmin.config;

import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class RabbitTopologyConfig {

    @Bean
    public TopicExchange eventosExchange() {
        return new TopicExchange(RabbitNames.EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(RabbitNames.DLX, true, false);
    }

    @Bean
    public Queue confirmacionQueue() {
        return QueueBuilder.durable(RabbitNames.Q_CONFIRMACION)
                .withArgument("x-dead-letter-exchange", RabbitNames.DLX)
                .withArgument("x-dead-letter-routing-key", RabbitNames.RK_DLQ)
                .build();
    }

    @Bean
    public Queue confirmacionDlq() {
        return QueueBuilder.durable(RabbitNames.Q_CONFIRMACION_DLQ).build();
    }

    @Bean
    public Binding confirmacionBinding(@Qualifier("confirmacionQueue") Queue queue,
                                       TopicExchange eventosExchange) {
        return BindingBuilder.bind(queue).to(eventosExchange).with(RabbitNames.RK_PEDIDO_CONFIRMADO);
    }

    @Bean
    public Binding pendienteBinding(@Qualifier("confirmacionQueue") Queue queue,
                                    TopicExchange eventosExchange) {
        return BindingBuilder.bind(queue).to(eventosExchange).with(RabbitNames.RK_PEDIDO_PENDIENTE);
    }

    @Bean
    public Binding confirmacionDlqBinding(@Qualifier("confirmacionDlq") Queue queue,
                                          DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(queue).to(deadLetterExchange).with(RabbitNames.RK_DLQ);
    }

    @Bean
    public ApplicationRunner rabbitTopologyInitializer(AmqpAdmin rabbitAdmin) {
        // Esta aplicación no tiene listeners que abran una conexión al arrancar.
        // Declarar los beans mediante el RabbitAdmin autoconfigurado por Spring Boot.
        return args -> rabbitAdmin.initialize();
    }
}
