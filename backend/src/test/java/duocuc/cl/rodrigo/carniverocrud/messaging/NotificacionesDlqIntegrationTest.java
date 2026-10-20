package duocuc.cl.rodrigo.carniverocrud.messaging;

import duocuc.cl.rodrigo.carniverocrud.service.EmailService;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.io.ClassPathResource;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Optativa: requiere un vhost demo.phase6.* exclusivo, con topología provisionada externamente.
 * No declara recursos ni usa SMTP real. Añade dos mensajes a su DLQ sin retirar los anteriores.
 */
@EnabledIfEnvironmentVariable(named = "CARNIVARIO_DLQ_TEST_VHOST", matches = "demo\\.phase6\\.[a-zA-Z0-9]+")
@ExtendWith(OutputCaptureExtension.class)
class NotificacionesDlqIntegrationTest {
    @Test
    void invalidEventAndMalformedJsonReachIsolatedDlqWithoutMail(CapturedOutput output) throws Exception {
        String vhost = System.getenv("CARNIVARIO_DLQ_TEST_VHOST");
        assertTrue(vhost.matches("demo\\.phase6\\.[a-zA-Z0-9]+"));
        var sources = new YamlPropertySourceLoader().load("backend", new ClassPathResource("application.yml"));
        EmailService email = mock(EmailService.class);
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(RabbitAutoConfiguration.class))
                .withUserConfiguration(RabbitConfig.class, NotificationListenerConfig.class,
                        NotificacionesListener.class)
                // Registrar el mock ya construido evita inyectar el campo SMTP de EmailService.
                .withInitializer(context -> context.getBeanFactory().registerSingleton("emailService", email))
                .withPropertyValues("spring.rabbitmq.virtual-host=" + vhost,
                        "spring.rabbitmq.listener.simple.auto-startup=false")
                .withInitializer(context -> sources.forEach(source ->
                        context.getEnvironment().getPropertySources().addLast(source)))
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertTrue(context.getBeansOfType(Declarable.class).isEmpty());
                    RabbitTemplate template = context.getBean(RabbitTemplate.class);
                    assertEquals(vhost, template.getConnectionFactory().getVirtualHost());
                    int existingDlqCount = template.execute(channel -> {
                        var main = channel.queueDeclarePassive(RabbitConfig.Q_CONFIRMACION);
                        var dlq = channel.queueDeclarePassive(RabbitConfig.Q_CONFIRMACION_DLQ);
                        assertEquals(0, main.getMessageCount(), "La cola de entrada de prueba debe estar vacía");
                        assertEquals(0, main.getConsumerCount(), "El vhost debe estar aislado");
                        assertEquals(0, dlq.getConsumerCount(), "La DLQ no debe tener consumidores");
                        return dlq.getMessageCount();
                    });
                    var registry = context.getBean(RabbitListenerEndpointRegistry.class);
                    assertEquals(1, registry.getListenerContainers().size());
                    registry.start();
                    try {
                        var invalid = new PedidoConfirmadoEvent(600001, "email-invalido",
                                "20/10/2026 12:00", "PENDIENTE", 100,
                                List.of(new PedidoConfirmadoEvent.Item("Prueba aislada", 1, 100)));
                        var eventProperties = properties("phase6-invalid-event");
                        Message event = template.getMessageConverter().toMessage(invalid, eventProperties);
                        template.send(RabbitConfig.EXCHANGE, RabbitConfig.RK_PEDIDO_PENDIENTE, event);
                        template.send(RabbitConfig.EXCHANGE, RabbitConfig.RK_PEDIDO_CONFIRMADO,
                                new Message("{invalid-json".getBytes(StandardCharsets.UTF_8),
                                        properties("phase6-invalid-json")));
                        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(200))
                                .untilAsserted(() -> assertEquals(existingDlqCount + 2, template.<Integer>execute(channel ->
                                        channel.queueDeclarePassive(RabbitConfig.Q_CONFIRMACION_DLQ).getMessageCount())));
                    } finally {
                        registry.stop();
                    }
                    verifyNoInteractions(email);
                    assertEquals(0, template.<Integer>execute(channel ->
                            channel.queueDeclarePassive(RabbitConfig.Q_CONFIRMACION).getMessageCount()));
                    assertEquals(existingDlqCount + 2, template.<Integer>execute(channel ->
                            channel.queueDeclarePassive(RabbitConfig.Q_CONFIRMACION_DLQ).getMessageCount()));
                });
        assertFalse(output.getAll().contains("email-invalido"));
        assertFalse(output.getAll().contains("{invalid-json"));
        assertFalse(output.getAll().contains("Caused by:"));
    }

    private static MessageProperties properties(String id) {
        var properties = new MessageProperties();
        properties.setMessageId(id);
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        return properties;
    }
}
