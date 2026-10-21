package duocuc.cl.rodrigo.carniverocrud.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.GetResponse;
import duocuc.cl.rodrigo.carniverocrud.service.EmailService;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.eclipse.angus.mail.smtp.SMTPSendFailedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Broker real, vhost exclusivo provisionado por RabbitAdmin y SMTP simulado.
 * No arranca el backend completo ni declara, elimina o purga topología.
 */
@EnabledIfEnvironmentVariable(named = "CARNIVARIO_PHASE7_VHOST", matches = "demo\\.phase7\\.[a-f0-9]+")
class Phase7MessagingIntegrationTest {
    private static final Duration DEADLINE = Duration.ofSeconds(15);

    @ParameterizedTest
    @ValueSource(strings = {"PENDIENTE", "COMPLETADA"})
    void eventIsRetainedWithoutConsumerThenDeserializedAndAcked(String state) throws Exception {
        run(f -> {
            var event = event(state);
            String route = "PENDIENTE".equals(state)
                    ? RabbitConfig.RK_PEDIDO_PENDIENTE : RabbitConfig.RK_PEDIDO_CONFIRMADO;
            // Usa el productor real y el converter existente.
            f.publisher.publicar(route, event);
            f.awaitMain(1);
            assertEquals(0, f.consumerCount());
            verifyNoInteractions(f.email);
            Thread.sleep(200);
            assertEquals(1, f.mainCount());
            f.registry.start();
            f.awaitAck(event);
            Delivery delivery = f.deliveries.get(0);
            assertEquals(route, delivery.message.getMessageProperties().getReceivedRoutingKey());
            assertEquals(new ObjectMapper().valueToTree(event),
                    new ObjectMapper().readTree(delivery.message.getBody()));
            assertEquals(f.initialDlq, f.dlqCount());
        });
    }

    @Test
    void stoppedConsumerRetainsMessageAndRestartProcessesItWithAdminStopped() throws Exception {
        run(f -> {
            var first = event("PENDIENTE");
            f.registry.start();
            f.publisher.publicar(RabbitConfig.RK_PEDIDO_PENDIENTE, first);
            f.awaitAck(first);
            f.registry.stop();
            assertEquals(0, f.consumerCount());
            var second = event("COMPLETADA");
            f.publisher.publicar(RabbitConfig.RK_PEDIDO_CONFIRMADO, second);
            f.awaitMain(1);
            verify(f.email, never()).enviarConfirmacionCompra(second);
            f.registry.start();
            f.awaitAck(second);
            assertEquals(2, f.deliveries.size());
        });
    }

    @Test
    void smtp451RetriesThenAcks() throws Exception {
        run(f -> {
            var event = event("PENDIENTE");
            doThrow(smtp(451)).doNothing().when(f.email).enviarConfirmacionCompra(event);
            f.registry.start();
            f.publisher.publicar(RabbitConfig.RK_PEDIDO_PENDIENTE, event);
            f.awaitAck(event, 2);
            assertEquals(f.initialDlq, f.dlqCount());
        });
    }

    @Test
    void exhaustedSmtp451HasThreeAttemptsAndOneNack() throws Exception {
        run(f -> {
            var event = event("PENDIENTE");
            doThrow(smtp(451)).when(f.email).enviarConfirmacionCompra(event);
            f.registry.start();
            Message sent = f.publish(event);
            f.awaitDeadLetter(sent);
            verify(f.email, times(3)).enviarConfirmacionCompra(event);
            f.verifySingleNack();
        });
    }

    @Test
    void smtp550IsRejectedWithoutRetry() throws Exception {
        run(f -> {
            var event = event("COMPLETADA");
            doThrow(smtp(550)).when(f.email).enviarConfirmacionCompra(event);
            f.registry.start();
            f.awaitDeadLetter(f.publish(event));
            verify(f.email).enviarConfirmacionCompra(event);
            f.verifySingleNack();
        });
    }

    @Test
    void authenticationFailureIsPermanentAndReachesDlq() throws Exception {
        run(f -> {
            var event = event("PENDIENTE");
            doThrow(new MailAuthenticationException("simulado")).when(f.email).enviarConfirmacionCompra(event);
            f.registry.start();
            f.awaitDeadLetter(f.publish(event));
            verify(f.email).enviarConfirmacionCompra(event);
            f.verifySingleNack();
        });
    }

    @Test
    void invalidEventIsNackedAndDoesNotBlockFollowingValidEvent() throws Exception {
        run(f -> {
            var invalid = new PedidoConfirmadoEvent(700001, "invalid", "fecha", "PENDIENTE", 1, List.of());
            Message sent = f.publish(invalid);
            var valid = event("COMPLETADA");
            f.publisher.publicar(RabbitConfig.RK_PEDIDO_CONFIRMADO, valid);
            f.awaitMain(2);
            f.registry.start();
            f.awaitDeadLetter(sent);
            f.awaitAck(valid);
            verify(f.email, never()).enviarConfirmacionCompra(invalid);
            Delivery rejected = f.deliveries.get(0);
            verify(rejected.channel).basicNack(rejected.tag(), false, false);
            verify(rejected.channel, never()).basicAck(anyLong(), anyBoolean());
        });
    }

    @Test
    void malformedJsonIsRejectedBeforeEmailAndDoesNotLoop() throws Exception {
        run(f -> {
            var props = properties();
            Message sent = new Message("{phase7-invalid-json".getBytes(StandardCharsets.UTF_8), props);
            f.registry.start();
            f.template.send(RabbitConfig.EXCHANGE, RabbitConfig.RK_PEDIDO_PENDIENTE, sent);
            f.awaitDeadLetter(sent);
            verifyNoInteractions(f.email);
            assertEquals(1, f.deliveries.size());
        });
    }

    private void run(Scenario scenario) throws Exception {
        String vhost = System.getenv("CARNIVARIO_PHASE7_VHOST");
        assertTrue(vhost.matches("demo\\.phase7\\.[a-f0-9]+"));
        // La prueba necesita que el proceso REST de RabbitAdmin esté detenido.
        try (Socket socket = new Socket()) {
            assertThrows(java.io.IOException.class,
                    () -> socket.connect(new InetSocketAddress("127.0.0.1", 8082), 500));
        }
        var sources = new YamlPropertySourceLoader().load("backend", new ClassPathResource("application.yml"));
        EmailService email = mock(EmailService.class);
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(RabbitAutoConfiguration.class))
                .withUserConfiguration(RabbitConfig.class, NotificationListenerConfig.class,
                        NotificacionesListener.class, EventPublisher.class)
                .withInitializer(context -> {
                    context.getBeanFactory().registerSingleton("emailService", email);
                    sources.forEach(source -> context.getEnvironment().getPropertySources().addLast(source));
                })
                .withPropertyValues("spring.rabbitmq.virtual-host=" + vhost,
                        "spring.rabbitmq.listener.simple.auto-startup=false")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertTrue(context.getBeansOfType(Declarable.class).isEmpty());
                    var fixture = new Fixture(context.getBean(RabbitTemplate.class),
                            context.getBean(EventPublisher.class),
                            context.getBean(RabbitListenerEndpointRegistry.class), email);
                    assertEquals(vhost, fixture.template.getConnectionFactory().getVirtualHost());
                    assertEquals(0, fixture.mainCount());
                    assertEquals(0, fixture.consumerCount());
                    try {
                        scenario.run(fixture);
                    } finally {
                        fixture.registry.stop();
                    }
                    assertEquals(0, fixture.mainCount());
                    assertEquals(0, fixture.consumerCount());
                });
    }

    private static final class Fixture {
        final RabbitTemplate template;
        final EventPublisher publisher;
        final RabbitListenerEndpointRegistry registry;
        final EmailService email;
        final List<Delivery> deliveries = new CopyOnWriteArrayList<>();
        final int initialDlq;

        Fixture(RabbitTemplate template, EventPublisher publisher,
                RabbitListenerEndpointRegistry registry, EmailService email) {
            this.template = template;
            this.publisher = publisher;
            this.registry = registry;
            this.email = email;
            initialDlq = dlqCount();
            assertEquals(1, registry.getListenerContainers().size());
            var container = (SimpleMessageListenerContainer) registry.getListenerContainers().iterator().next();
            var realAdapter = (ChannelAwareMessageListener) container.getMessageListener();
            container.setMessageListener((ChannelAwareMessageListener) (message, channel) -> {
                // El decorador ejecuta ACK/NACK sobre el canal real y permite verificar sus llamadas.
                Channel observed = mock(Channel.class, delegatesTo(channel));
                deliveries.add(new Delivery(message, observed));
                realAdapter.onMessage(message, observed);
            });
        }

        int mainCount() { return count(RabbitConfig.Q_CONFIRMACION); }
        int dlqCount() { return count(RabbitConfig.Q_CONFIRMACION_DLQ); }
        int count(String queue) {
            return template.execute(channel -> channel.queueDeclarePassive(queue).getMessageCount());
        }
        int consumerCount() {
            return template.execute(channel ->
                    channel.queueDeclarePassive(RabbitConfig.Q_CONFIRMACION).getConsumerCount());
        }
        void awaitMain(int expected) {
            await().atMost(DEADLINE).untilAsserted(() -> assertEquals(expected, mainCount()));
        }
        void awaitAck(PedidoConfirmadoEvent event) { awaitAck(event, 1); }
        void awaitAck(PedidoConfirmadoEvent event, int attempts) {
            await().atMost(DEADLINE).untilAsserted(() -> {
                verify(email, times(attempts)).enviarConfirmacionCompra(event);
                Delivery delivery = deliveries.get(deliveries.size() - 1);
                verify(delivery.channel).basicAck(delivery.tag(), false);
                verify(delivery.channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
                assertEquals(0, mainCount());
            });
        }
        Message publish(PedidoConfirmadoEvent event) {
            Message message = template.getMessageConverter().toMessage(event, properties());
            template.send(RabbitConfig.EXCHANGE, RabbitConfig.RK_PEDIDO_PENDIENTE, message);
            return message;
        }
        void verifySingleNack() throws Exception {
            assertEquals(1, deliveries.size());
            Delivery delivery = deliveries.get(0);
            verify(delivery.channel).basicNack(delivery.tag(), false, false);
            verify(delivery.channel, never()).basicAck(anyLong(), anyBoolean());
        }
        void awaitDeadLetter(Message sent) throws Exception {
            await().atMost(DEADLINE).untilAsserted(() -> assertEquals(initialDlq + 1, dlqCount()));
            GetResponse dead = template.execute(channel -> {
                List<Long> inspected = new ArrayList<>();
                GetResponse match = null;
                try {
                    // Inspección puntual de la DLQ aislada; nunca se registra un consumidor DLQ.
                    for (int i = 0; i < initialDlq + 1; i++) {
                        GetResponse candidate = channel.basicGet(RabbitConfig.Q_CONFIRMACION_DLQ, false);
                        assertNotNull(candidate);
                        inspected.add(candidate.getEnvelope().getDeliveryTag());
                        if (sent.getMessageProperties().getMessageId().equals(candidate.getProps().getMessageId())) {
                            match = candidate;
                        }
                    }
                    assertNotNull(match);
                    return match;
                } finally {
                    // Conservar todos los mensajes de prueba inspeccionados.
                    for (long tag : inspected) channel.basicNack(tag, false, true);
                }
            });
            assertNotNull(dead);
            assertArrayEquals(sent.getBody(), dead.getBody());
            assertEquals(RabbitConfig.DLX, dead.getEnvelope().getExchange());
            assertEquals(RabbitConfig.Q_CONFIRMACION_DLQ, dead.getEnvelope().getRoutingKey());
            List<?> deaths = (List<?>) dead.getProps().getHeaders().get("x-death");
            assertNotNull(deaths);
            Map<?, ?> death = (Map<?, ?>) deaths.get(0);
            assertEquals("rejected", death.get("reason").toString());
            assertEquals(1L, ((Number) death.get("count")).longValue());
            assertEquals(RabbitConfig.Q_CONFIRMACION, death.get("queue").toString());
            assertEquals(RabbitConfig.EXCHANGE, death.get("exchange").toString());
            Thread.sleep(200);
            assertEquals(initialDlq + 1, dlqCount());
            assertEquals(0, mainCount());
        }
    }

    private record Delivery(Message message, Channel channel) {
        long tag() { return message.getMessageProperties().getDeliveryTag(); }
    }

    @FunctionalInterface
    private interface Scenario { void run(Fixture fixture) throws Exception; }

    private static PedidoConfirmadoEvent event(String state) {
        return new PedidoConfirmadoEvent(700001, "phase7@example.invalid", "20/10/2026 12:00",
                state, 100, List.of(new PedidoConfirmadoEvent.Item("Planta de prueba", 1, 100)));
    }
    private static MessageProperties properties() {
        var properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setMessageId("phase7-" + UUID.randomUUID());
        return properties;
    }
    private static MailSendException smtp(int code) {
        return new MailSendException("simulado",
                new SMTPSendFailedException("DATA", code, "simulado", null, null, null, null));
    }
}
