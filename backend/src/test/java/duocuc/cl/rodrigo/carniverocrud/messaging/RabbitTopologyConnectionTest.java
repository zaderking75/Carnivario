package duocuc.cl.rodrigo.carniverocrud.messaging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.*;

/** Consulta pasiva optativa: no declara recursos, consume mensajes ni inicia SMTP/BD/Azure. */
@EnabledIfEnvironmentVariable(named = "CARNIVARIO_RABBIT_INTEGRATION", matches = "true")
class RabbitTopologyConnectionTest {

    @Test
    void backendAmqpConfigurationConnectsToPreviouslyDeclaredTopology() throws Exception {
        var sources = new YamlPropertySourceLoader().load("backend", new ClassPathResource("application.yml"));
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(RabbitAutoConfiguration.class))
                .withUserConfiguration(RabbitConfig.class)
                .withInitializer(context -> sources.forEach(source ->
                        context.getEnvironment().getPropertySources().addLast(source)))
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertTrue(context.getBeansOfType(Declarable.class).isEmpty());
                    assertTrue(context.getBeansOfType(Declarables.class).isEmpty());
                    assertTrue(context.getBeansOfType(NotificacionesListener.class).isEmpty());
                    assertTrue(context.getBeansOfType(JavaMailSender.class).isEmpty());
                    context.getBeansOfType(RabbitListenerEndpointRegistry.class).values().forEach(registry ->
                            assertTrue(registry.getListenerContainers().isEmpty()));

                    RabbitTemplate template = context.getBean(RabbitTemplate.class);
                    assertSame(context.getBean(MessageConverter.class), template.getMessageConverter());
                    Boolean verified = template.execute(channel -> {
                        assertNotNull(channel.exchangeDeclarePassive(RabbitConfig.EXCHANGE));
                        assertNotNull(channel.exchangeDeclarePassive(RabbitConfig.DLX));
                        assertEquals(RabbitConfig.Q_CONFIRMACION,
                                channel.queueDeclarePassive(RabbitConfig.Q_CONFIRMACION).getQueue());
                        assertEquals(RabbitConfig.Q_CONFIRMACION_DLQ,
                                channel.queueDeclarePassive(RabbitConfig.Q_CONFIRMACION_DLQ).getQueue());
                        return true;
                    });
                    assertEquals(Boolean.TRUE, verified);
                });
    }
}
