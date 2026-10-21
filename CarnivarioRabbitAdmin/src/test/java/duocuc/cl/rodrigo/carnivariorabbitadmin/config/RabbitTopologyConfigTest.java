package duocuc.cl.rodrigo.carnivariorabbitadmin.config;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RabbitTopologyConfigTest {

    private AnnotationConfigApplicationContext context;
    private AmqpAdmin rabbitAdmin;

    @BeforeEach
    void createContextWithoutBroker() {
        rabbitAdmin = mock(AmqpAdmin.class);
        context = new AnnotationConfigApplicationContext();
        context.registerBean("rabbitAdmin", AmqpAdmin.class, () -> rabbitAdmin);
        context.register(RabbitTopologyConfig.class);
        context.refresh();
    }

    @AfterEach
    void closeContext() {
        context.close();
    }

    @Test
    void namesPreserveTheExistingContractAndAddPendingRouting() {
        assertAll(
                () -> assertEquals("carnivario.eventos", RabbitNames.EXCHANGE),
                () -> assertEquals("carnivario.dlx", RabbitNames.DLX),
                () -> assertEquals("notificaciones.confirmacion.queue", RabbitNames.Q_CONFIRMACION),
                () -> assertEquals("notificaciones.confirmacion.dlq", RabbitNames.Q_CONFIRMACION_DLQ),
                () -> assertEquals("pedido.confirmado", RabbitNames.RK_PEDIDO_CONFIRMADO),
                () -> assertEquals("pedido.pendiente", RabbitNames.RK_PEDIDO_PENDIENTE),
                () -> assertEquals(RabbitNames.Q_CONFIRMACION_DLQ, RabbitNames.RK_DLQ));
    }

    @Test
    void declaresExactlyTwoDurableExchangesWithTheCorrectTypes() {
        Map<String, Exchange> exchanges = context.getBeansOfType(Exchange.class);
        assertEquals(2, exchanges.size());
        TopicExchange events = context.getBean(TopicExchange.class);
        DirectExchange dlx = context.getBean(DirectExchange.class);
        assertEquals(RabbitNames.EXCHANGE, events.getName());
        assertEquals("topic", events.getType());
        assertEquals(RabbitNames.DLX, dlx.getName());
        assertEquals("direct", dlx.getType());
        exchanges.values().forEach(exchange -> {
            assertTrue(exchange.isDurable());
            assertFalse(exchange.isAutoDelete());
            assertFalse(exchange.isInternal());
        });
    }

    @Test
    void declaresExactlyTwoDurableNonExclusiveQueues() {
        Map<String, Queue> queues = context.getBeansOfType(Queue.class);
        assertEquals(2, queues.size());
        assertEquals(Set.of(RabbitNames.Q_CONFIRMACION, RabbitNames.Q_CONFIRMACION_DLQ),
                queues.values().stream().map(Queue::getName).collect(Collectors.toSet()));
        queues.values().forEach(queue -> {
            assertTrue(queue.isDurable());
            assertFalse(queue.isExclusive());
            assertFalse(queue.isAutoDelete());
        });
    }

    @Test
    void preservesOnlyTheOriginalDeadLetterArgumentsWithoutTtl() {
        Queue main = context.getBean("confirmacionQueue", Queue.class);
        Queue dlq = context.getBean("confirmacionDlq", Queue.class);
        assertEquals(Map.of(
                "x-dead-letter-exchange", RabbitNames.DLX,
                "x-dead-letter-routing-key", RabbitNames.RK_DLQ), main.getArguments());
        assertTrue(dlq.getArguments().isEmpty());
    }

    @Test
    void declaresExactlyThreeExplicitBindingsToTheExpectedQueues() {
        Map<String, Binding> bindings = context.getBeansOfType(Binding.class);
        assertEquals(3, bindings.size());
        record Route(String exchange, String queue, String routingKey) { }
        Set<Route> actual = bindings.values().stream()
                .map(binding -> new Route(binding.getExchange(), binding.getDestination(), binding.getRoutingKey()))
                .collect(Collectors.toSet());
        assertEquals(Set.of(
                new Route(RabbitNames.EXCHANGE, RabbitNames.Q_CONFIRMACION, RabbitNames.RK_PEDIDO_CONFIRMADO),
                new Route(RabbitNames.EXCHANGE, RabbitNames.Q_CONFIRMACION, RabbitNames.RK_PEDIDO_PENDIENTE),
                new Route(RabbitNames.DLX, RabbitNames.Q_CONFIRMACION_DLQ, RabbitNames.RK_DLQ)), actual);
        bindings.values().forEach(binding -> {
            assertEquals(Binding.DestinationType.QUEUE, binding.getDestinationType());
            assertTrue(binding.getArguments().isEmpty());
        });
    }

    @Test
    void initializesTopologyOnApplicationStartupWithoutAListener() throws Exception {
        verifyNoInteractions(rabbitAdmin);
        context.getBean(ApplicationRunner.class).run(new DefaultApplicationArguments());
        verify(rabbitAdmin).initialize();
        verifyNoMoreInteractions(rabbitAdmin);
    }

    @Test
    void propagatesDeclarationFailuresWithoutDeletingResources() {
        AmqpConnectException failure = new AmqpConnectException(new java.net.ConnectException("Broker unavailable"));
        doThrow(failure).when(rabbitAdmin).initialize();
        assertSame(failure, assertThrows(AmqpConnectException.class,
                () -> context.getBean(ApplicationRunner.class).run(new DefaultApplicationArguments())));
        verify(rabbitAdmin).initialize();
        verifyNoMoreInteractions(rabbitAdmin);
    }
}
