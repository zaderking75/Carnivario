package duocuc.cl.rodrigo.carniverocrud.messaging;

import com.rabbitmq.client.Channel;
import duocuc.cl.rodrigo.carniverocrud.service.EmailService;
import java.io.IOException;
import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import jakarta.mail.MessagingException;
import org.eclipse.angus.mail.smtp.SMTPSendFailedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.BlockingQueueConsumer;
import org.springframework.amqp.rabbit.listener.MethodRabbitListenerEndpoint;
import org.springframework.amqp.rabbit.listener.adapter.MessagingMessageListenerAdapter;
import org.springframework.amqp.rabbit.support.ActiveObjectCounter;
import org.springframework.amqp.rabbit.support.DefaultMessagePropertiesConverter;
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailSendException;
import org.springframework.messaging.handler.annotation.support.DefaultMessageHandlerMethodFactory;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class NotificacionesListenerTest {
    @Mock EmailService emailService;
    @Mock Channel channel;
    @Mock NotificacionesListener.RetryWait wait;
    NotificacionesListener listener;
    private static final long TAG = 42;

    @BeforeEach
    void setUp() {
        listener = new NotificacionesListener(emailService, wait);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDIENTE", "COMPLETADA"})
    void sendsBeforeAckAndNeverNacks(String state) throws Exception {
        var event = event(1, "test@example.invalid", "20/10/2026 12:00", state, 100, items());
        listener.onPedidoConfirmado(event, channel, TAG);
        var order = inOrder(emailService, channel);
        order.verify(emailService).enviarConfirmacionCompra(event);
        order.verify(channel).basicAck(TAG, false);
        verifyNoMoreInteractions(emailService, channel);
        verifyNoInteractions(wait);
    }

    @ParameterizedTest
    @MethodSource("invalidEvents")
    void rejectsInvalidEventWithoutMail(PedidoConfirmadoEvent event) throws Exception {
        listener.onPedidoConfirmado(event, channel, TAG);
        verify(channel).basicNack(TAG, false, false);
        verifyNoMoreInteractions(channel);
        verifyNoInteractions(emailService, wait);
    }

    static Stream<PedidoConfirmadoEvent> invalidEvents() {
        return Stream.of(null,
                event(null, "test@example.invalid", "fecha", "PENDIENTE", 100, items()),
                event(0, "test@example.invalid", "fecha", "PENDIENTE", 100, items()),
                event(-1, "test@example.invalid", "fecha", "PENDIENTE", 100, items()),
                event(1, null, "fecha", "PENDIENTE", 100, items()),
                event(1, "", "fecha", "PENDIENTE", 100, items()),
                event(1, "bad-address", "fecha", "PENDIENTE", 100, items()),
                event(1, "a@b.invalid\r\nBcc:other@example.invalid", "fecha", "PENDIENTE", 100, items()),
                event(1, "test@example.invalid", "fecha", null, 100, items()),
                event(1, "test@example.invalid", "fecha", "CANCELADA", 100, items()),
                event(1, "test@example.invalid", null, "PENDIENTE", 100, items()),
                event(1, "test@example.invalid", " ", "PENDIENTE", 100, items()),
                event(1, "test@example.invalid", "fecha", "PENDIENTE", -1, items()),
                event(1, "test@example.invalid", "fecha", "PENDIENTE", 100, null),
                event(1, "test@example.invalid", "fecha", "PENDIENTE", 100, List.of()),
                event(1, "test@example.invalid", "fecha", "PENDIENTE", 100, Arrays.asList((PedidoConfirmadoEvent.Item) null)),
                event(1, "test@example.invalid", "fecha", "PENDIENTE", 100, List.of(new PedidoConfirmadoEvent.Item(null, 1, 100))),
                event(1, "test@example.invalid", "fecha", "PENDIENTE", 100, List.of(new PedidoConfirmadoEvent.Item(" ", 1, 100))),
                event(1, "test@example.invalid", "fecha", "PENDIENTE", 100, List.of(new PedidoConfirmadoEvent.Item("Planta", 0, 100))),
                event(1, "test@example.invalid", "fecha", "PENDIENTE", 100, List.of(new PedidoConfirmadoEvent.Item("Planta", 1, -1))));
    }

    @Test
    void retriesTransientFailureThenAcks() throws Exception {
        doThrow(transientFailure()).doNothing().when(emailService).enviarConfirmacionCompra(validEvent());
        listener.onPedidoConfirmado(validEvent(), channel, TAG);
        var order = inOrder(emailService, wait, channel);
        order.verify(emailService).enviarConfirmacionCompra(validEvent());
        order.verify(wait).pause(1000);
        order.verify(emailService).enviarConfirmacionCompra(validEvent());
        order.verify(channel).basicAck(TAG, false);
        verifyNoMoreInteractions(emailService, wait, channel);
    }

    @Test
    void exhaustsExactlyThreeAttemptsWithBoundedWaits() throws Exception {
        doThrow(transientFailure()).when(emailService).enviarConfirmacionCompra(validEvent());
        listener.onPedidoConfirmado(validEvent(), channel, TAG);
        verify(emailService, times(3)).enviarConfirmacionCompra(validEvent());
        var order = inOrder(wait, channel);
        order.verify(wait).pause(1000);
        order.verify(wait).pause(2000);
        order.verify(channel).basicNack(TAG, false, false);
        verifyNoMoreInteractions(emailService, wait, channel);
    }

    @ParameterizedTest
    @MethodSource("permanentFailures")
    void permanentAndUnknownFailuresAreNotRetried(RuntimeException failure) throws Exception {
        doThrow(failure).when(emailService).enviarConfirmacionCompra(validEvent());
        listener.onPedidoConfirmado(validEvent(), channel, TAG);
        verify(emailService).enviarConfirmacionCompra(validEvent());
        verify(channel).basicNack(TAG, false, false);
        verifyNoMoreInteractions(emailService, channel);
        verifyNoInteractions(wait);
    }

    static Stream<RuntimeException> permanentFailures() {
        return Stream.of(new MailAuthenticationException("secret"), new MailParseException("private"),
                new MailSendException("unknown"), new IllegalArgumentException("invalid"),
                smtpFailure(550), new MailSendException("nested",
                        new jakarta.mail.AuthenticationFailedException("private")));
    }

    @Test
    void recognizesSmtp4xxAndNestedConnectionErrors() {
        assertTrue(NotificationRetryPolicy.isRetryable(smtpFailure(451)));
        var nested = new MessagingException("mail");
        nested.setNextException(new ConnectException("private"));
        assertTrue(NotificationRetryPolicy.isRetryable(new MailSendException("wrapper", nested)));
        assertTrue(NotificationRetryPolicy.isRetryable(
                new MailSendException(Map.of("message", new ConnectException("private")))));
        assertFalse(NotificationRetryPolicy.isRetryable(new MailSendException(Map.of(
                "a", new ConnectException("private"), "b", new jakarta.mail.AuthenticationFailedException()))));
    }

    @Test
    void interruptionStopsRetriesAndRestoresFlag() throws Exception {
        doThrow(transientFailure()).when(emailService).enviarConfirmacionCompra(validEvent());
        doThrow(new InterruptedException()).when(wait).pause(1000);
        try {
            listener.onPedidoConfirmado(validEvent(), channel, TAG);
            assertTrue(Thread.currentThread().isInterrupted());
            verify(emailService).enviarConfirmacionCompra(validEvent());
            verify(channel).basicNack(TAG, false, false);
            verifyNoMoreInteractions(emailService, channel);
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void ackFailurePropagatesWithoutNackAndWarnsOfRedelivery(CapturedOutput output) throws Exception {
        var failure = new IOException("secret");
        doThrow(failure).when(channel).basicAck(TAG, false);
        assertSame(failure, assertThrows(IOException.class,
                () -> listener.onPedidoConfirmado(validEvent(), channel, TAG)));
        verify(channel).basicAck(TAG, false);
        verifyNoMoreInteractions(channel);
        assertTrue(output.getAll().contains("posible redelivery"));
        assertFalse(output.getAll().contains("ACK enviado"));
        assertFalse(output.getAll().contains("secret"));
    }

    @Test
    void nackFailurePropagatesWithoutAckOrSecondNack(CapturedOutput output) throws Exception {
        doThrow(new IOException("secret")).when(channel).basicNack(TAG, false, false);
        assertThrows(IOException.class, () -> listener.onPedidoConfirmado(null, channel, TAG));
        verify(channel).basicNack(TAG, false, false);
        verifyNoMoreInteractions(channel);
        assertFalse(output.getAll().contains("NACK enviado"));
    }

    @Test
    void usesDeliveryTagOnlyOnItsOwnChannel() throws Exception {
        var otherChannel = mock(Channel.class);
        listener.onPedidoConfirmado(validEvent(), channel, TAG);
        listener.onPedidoConfirmado(null, otherChannel, TAG);
        verify(channel).basicAck(TAG, false);
        verify(otherChannel).basicNack(TAG, false, false);
        verifyNoMoreInteractions(channel, otherChannel);
    }

    @Test
    void logsDoNotIncludeAddressesPayloadOrSmtpExceptionMessage(CapturedOutput output) throws Exception {
        doThrow(new MailAuthenticationException("smtp-password-private")).when(emailService)
                .enviarConfirmacionCompra(validEvent());
        listener.onPedidoConfirmado(validEvent(), channel, TAG);
        assertTrue(output.getAll().contains("compraId=1 estado=PENDIENTE"));
        assertFalse(output.getAll().contains("smtp-password-private"));
        assertFalse(output.getAll().contains("test@example.invalid"));
        assertFalse(output.getAll().contains("Drosera"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void malformedJsonIsRejectedByContainerBeforeListenerEvenWithXDeath(boolean hasXDeath,
            CapturedOutput output) throws Exception {
        var adapter = new MessagingMessageListenerAdapter(listener, NotificacionesListener.class.getMethod(
                "onPedidoConfirmado", PedidoConfirmadoEvent.class, Channel.class, long.class));
        adapter.setMessageConverter(new RabbitConfig().jsonMessageConverter());
        var properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setDeliveryTag(TAG);
        if (hasXDeath) properties.setHeader("x-death", List.of(Map.of("count", 1L)));
        var message = new Message("{private-json-invalid".getBytes(StandardCharsets.UTF_8), properties);
        var conversion = assertThrows(ListenerExecutionFailedException.class,
                () -> adapter.onMessage(message, channel));
        var rejection = assertThrows(AmqpRejectAndDontRequeueException.class,
                () -> NotificationListenerConfig.errorHandler().handleError(conversion));
        assertTrue(rejection.isRejectManual());
        assertNull(rejection.getCause());
        assertEquals(0, rejection.getStackTrace().length);
        // Ejecutar la ruta real de rechazo del consumidor Spring, con canal Mockito.
        var consumer = new BlockingQueueConsumer(mock(ConnectionFactory.class),
                new DefaultMessagePropertiesConverter(), new ActiveObjectCounter<>(),
                AcknowledgeMode.MANUAL, false, 1, false, RabbitConfig.Q_CONFIRMACION);
        ReflectionTestUtils.setField(consumer, "channel", channel);
        consumer.rollbackOnExceptionIfNecessary(rejection, TAG);
        verify(channel).basicNack(TAG, false, false);
        verifyNoMoreInteractions(channel);
        verifyNoInteractions(emailService, wait);
        assertFalse(output.getAll().contains("private-json-invalid"));
    }

    @Test
    void bootFactoryUsesYamlManualAckAndExistingConverterWithoutTopology() throws Exception {
        var sources = new YamlPropertySourceLoader().load("backend", new ClassPathResource("application.yml"));
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(RabbitAutoConfiguration.class))
                .withUserConfiguration(RabbitConfig.class, NotificationListenerConfig.class)
                .withPropertyValues("spring.rabbitmq.username=unused", "spring.rabbitmq.password=unused",
                        "spring.rabbitmq.listener.simple.auto-startup=false")
                .withInitializer(context -> sources.forEach(source ->
                        context.getEnvironment().getPropertySources().addLast(source)))
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertTrue(context.getBeansOfType(Declarable.class).isEmpty());
                    var endpoint = new MethodRabbitListenerEndpoint();
                    endpoint.setId("unit-notifications");
                    endpoint.setBean(listener);
                    endpoint.setMethod(NotificacionesListener.class.getMethod("onPedidoConfirmado",
                            PedidoConfirmadoEvent.class, Channel.class, long.class));
                    endpoint.setQueueNames(RabbitConfig.Q_CONFIRMACION);
                    var methodFactory = new DefaultMessageHandlerMethodFactory();
                    methodFactory.afterPropertiesSet();
                    endpoint.setMessageHandlerMethodFactory(methodFactory);
                    var factory = context.getBean("rabbitListenerContainerFactory",
                            SimpleRabbitListenerContainerFactory.class);
                    var container = factory.createListenerContainer(endpoint);
                    assertEquals(AcknowledgeMode.MANUAL, container.getAcknowledgeMode());
                    assertEquals(1, ReflectionTestUtils.getField(container, "prefetchCount"));
                    assertEquals(false, ReflectionTestUtils.getField(container, "defaultRequeueRejected"));
                    assertSame(context.getBean("jsonMessageConverter"),
                            ReflectionTestUtils.getField(container.getMessageListener(), "messageConverter"));
                    assertEquals(false, context.getEnvironment().getProperty(
                            "spring.rabbitmq.listener.simple.retry.enabled", Boolean.class));
                    container.destroy();
                });
    }

    static PedidoConfirmadoEvent validEvent() {
        return event(1, "test@example.invalid", "20/10/2026 12:00", "PENDIENTE", 100, items());
    }

    private static PedidoConfirmadoEvent event(Integer id, String email, String date, String state,
            int total, List<PedidoConfirmadoEvent.Item> items) {
        return new PedidoConfirmadoEvent(id, email, date, state, total, items);
    }

    private static List<PedidoConfirmadoEvent.Item> items() {
        return List.of(new PedidoConfirmadoEvent.Item("Drosera", 1, 100));
    }

    private static MailSendException transientFailure() {
        return new MailSendException("SMTP temporal", new ConnectException("simulado"));
    }

    private static MailSendException smtpFailure(int code) {
        return new MailSendException("SMTP", new SMTPSendFailedException(
                "DATA", code, "simulado", null, null, null, null));
    }
}
