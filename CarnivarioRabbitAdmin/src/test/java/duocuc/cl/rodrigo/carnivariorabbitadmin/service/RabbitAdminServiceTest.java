package duocuc.cl.rodrigo.carnivariorabbitadmin.service;

import java.io.IOException;
import java.net.ConnectException;
import java.util.stream.Stream;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ShutdownSignalException;
import duocuc.cl.rodrigo.carnivariorabbitadmin.dto.BindingRequest;
import duocuc.cl.rodrigo.carnivariorabbitadmin.dto.ExchangeRequest;
import duocuc.cl.rodrigo.carnivariorabbitadmin.dto.QueueRequest;
import duocuc.cl.rodrigo.carnivariorabbitadmin.service.RabbitAdminService.AdminOperationException;
import duocuc.cl.rodrigo.carnivariorabbitadmin.service.RabbitAdminService.Failure;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.AmqpIOException;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.ChannelCallback;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RabbitAdminServiceTest {

    private static ValidatorFactory validators;
    @Mock private AmqpAdmin admin;
    @Mock private RabbitTemplate template;
    @Mock private Channel channel;
    private RabbitAdminService service;

    @BeforeAll
    static void openValidator() {
        validators = Validation.buildDefaultValidatorFactory();
    }

    @AfterAll
    static void closeValidator() {
        validators.close();
    }

    @BeforeEach
    void setUp() {
        service = new RabbitAdminService(admin, template, validators.getValidator());
    }

    @Test
    void createsQueueUsingAmqpAdmin() {
        when(admin.declareQueue(any(Queue.class))).thenReturn("demo.queue");
        service.createQueue(new QueueRequest("demo.queue", true));
        ArgumentCaptor<Queue> queue = ArgumentCaptor.forClass(Queue.class);
        verify(admin).declareQueue(queue.capture());
        assertEquals("demo.queue", queue.getValue().getName());
        assertTrue(queue.getValue().isDurable());
        assertFalse(queue.getValue().isExclusive());
        assertFalse(queue.getValue().isAutoDelete());
        assertTrue(queue.getValue().getArguments().isEmpty());
        verifyNoInteractions(template);
    }

    @Test
    void deletesQueueOnlyAfterPassiveCheck() throws Exception {
        useMockChannel();
        queueExists("demo.queue");
        service.deleteQueue("demo.queue");
        var order = inOrder(channel, admin);
        order.verify(channel).queueDeclarePassive("demo.queue");
        order.verify(admin).deleteQueue("demo.queue", false, false);
        verifyNoMoreInteractions(admin);
    }

    @Test
    void readsQueueCountsFromBrokerWithoutMutation() throws Exception {
        useMockChannel();
        when(channel.queueDeclarePassive("demo.queue")).thenReturn(
                new AMQP.Queue.DeclareOk.Builder().queue("demo.queue").messageCount(7).consumerCount(2).build());
        assertEquals(new RabbitAdminService.QueueState("demo.queue", 7, 2), service.getQueue("demo.queue"));
        verifyNoInteractions(admin);
    }

    @ParameterizedTest
    @ValueSource(strings = {"direct", "topic", "fanout"})
    void createsSupportedExchanges(String type) {
        service.createExchange(new ExchangeRequest("demo.exchange", type, true));
        ArgumentCaptor<Exchange> exchange = ArgumentCaptor.forClass(Exchange.class);
        verify(admin).declareExchange(exchange.capture());
        assertEquals(type, exchange.getValue().getType());
        assertEquals("demo.exchange", exchange.getValue().getName());
        assertTrue(exchange.getValue().isDurable());
        assertFalse(exchange.getValue().isAutoDelete());
    }

    @Test
    void deletesExchangeAfterPassiveCheck() throws Exception {
        useMockChannel();
        exchangeExists("demo.exchange");
        when(admin.deleteExchange("demo.exchange")).thenReturn(true);
        service.deleteExchange("demo.exchange");
        var order = inOrder(channel, admin);
        order.verify(channel).exchangeDeclarePassive("demo.exchange");
        order.verify(admin).deleteExchange("demo.exchange");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void bindingOperationsCheckBothReferences(boolean create) throws Exception {
        useMockChannel();
        queueExists("demo.queue");
        exchangeExists("demo.exchange");
        BindingRequest request = new BindingRequest("demo.exchange", "demo.queue", "demo.*");
        if (create) service.createBinding(request); else service.deleteBinding(request);
        ArgumentCaptor<Binding> binding = ArgumentCaptor.forClass(Binding.class);
        var order = inOrder(channel, admin);
        order.verify(channel).queueDeclarePassive("demo.queue");
        order.verify(channel).exchangeDeclarePassive("demo.exchange");
        if (create) order.verify(admin).declareBinding(binding.capture());
        else order.verify(admin).removeBinding(binding.capture());
        assertEquals("demo.exchange", binding.getValue().getExchange());
        assertEquals("demo.queue", binding.getValue().getDestination());
        assertEquals("demo.*", binding.getValue().getRoutingKey());
        assertEquals(Binding.DestinationType.QUEUE, binding.getValue().getDestinationType());
        verifyNoMoreInteractions(admin);
    }

    @ParameterizedTest
    @ValueSource(strings = {"carnivario.eventos", "carnivario.dlx", "notificaciones.confirmacion.queue",
            "notificaciones.confirmacion.dlq", "other.queue", "demo", "demoevil.queue"})
    void rejectsEveryOperationOnNonDemoResourcesBeforeBrokerAccess(String name) {
        assertFailure(Failure.FORBIDDEN, () -> service.createQueue(new QueueRequest(name, true)));
        assertFailure(Failure.FORBIDDEN, () -> service.deleteQueue(name));
        assertFailure(Failure.FORBIDDEN, () -> service.getQueue(name));
        assertFailure(Failure.FORBIDDEN, () -> service.createExchange(new ExchangeRequest(name, "topic", true)));
        assertFailure(Failure.FORBIDDEN, () -> service.deleteExchange(name));
        for (BindingRequest binding : new BindingRequest[] {
                new BindingRequest(name, "demo.queue", "demo.key"),
                new BindingRequest("demo.exchange", name, "demo.key")}) {
            assertFailure(Failure.FORBIDDEN, () -> service.createBinding(binding));
            assertFailure(Failure.FORBIDDEN, () -> service.deleteBinding(binding));
        }
        verifyNoInteractions(admin, template);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "demo.", "demo..queue", "demo/queue", "demo.á"})
    void rejectsInvalidNamesEvenWhenCalledWithoutController(String name) {
        assertFailure(Failure.INVALID, () -> service.deleteQueue(name));
        assertFailure(Failure.INVALID, () -> service.deleteExchange(name));
        assertFailure(Failure.INVALID, () -> service.createQueue(new QueueRequest(name, true)));
        verifyNoInteractions(admin, template);
    }

    @Test
    void rejectsInvalidDtoValuesWithoutBrokerAccess() {
        assertFailure(Failure.INVALID, () -> service.createQueue(null));
        assertFailure(Failure.INVALID, () -> service.createQueue(new QueueRequest("demo.queue", null)));
        assertFailure(Failure.INVALID, () -> service.createQueue(new QueueRequest("demo." + "a".repeat(251), true)));
        assertFailure(Failure.INVALID, () -> service.createExchange(new ExchangeRequest("demo.exchange", "headers", true)));
        assertFailure(Failure.INVALID, () -> service.createBinding(new BindingRequest("demo.exchange", "demo.queue", "bad..key")));
        verifyNoInteractions(admin, template);
    }

    @ParameterizedTest
    @CsvSource({"404,NOT_FOUND", "403,FORBIDDEN", "405,CONFLICT", "406,CONFLICT", "541,UNAVAILABLE"})
    void translatesStructuredBrokerReplyCodes(int code, Failure expected) {
        doThrow(brokerError(code)).when(admin).declareExchange(any(Exchange.class));
        assertFailure(expected, () -> service.createExchange(new ExchangeRequest("demo.exchange", "topic", true)));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void rejectsBindingsWithMissingQueueOrExchange(boolean missingQueue) throws Exception {
        useMockChannel();
        if (missingQueue) when(channel.queueDeclarePassive("demo.queue")).thenThrow(brokerError(404));
        else {
            queueExists("demo.queue");
            when(channel.exchangeDeclarePassive("demo.exchange")).thenThrow(brokerError(404));
        }
        assertFailure(Failure.NOT_FOUND,
                () -> service.createBinding(new BindingRequest("demo.exchange", "demo.queue", "demo.key")));
        verifyNoInteractions(admin);
    }

    @Test
    void missingQueuePreventsDeleteAndReturnsNotFoundForGet() throws Exception {
        useMockChannel();
        when(channel.queueDeclarePassive("demo.missing")).thenThrow(brokerError(404));
        assertFailure(Failure.NOT_FOUND, () -> service.deleteQueue("demo.missing"));
        assertFailure(Failure.NOT_FOUND, () -> service.getQueue("demo.missing"));
        verifyNoInteractions(admin);
    }

    @Test
    void communicationFailureIsNotReportedAsMissingResource() {
        when(template.execute(any())).thenThrow(new AmqpConnectException(new ConnectException("private details")));
        assertFailure(Failure.UNAVAILABLE, () -> service.getQueue("demo.queue"));
        assertFailure(Failure.UNAVAILABLE, () -> service.deleteQueue("demo.queue"));
        assertFailure(Failure.UNAVAILABLE, () -> service.deleteExchange("demo.exchange"));
        verifyNoInteractions(admin);
    }

    @Test
    void creationPropagatesBrokerUnavailableWithoutLeakingCause() {
        when(admin.declareQueue(any(Queue.class))).thenThrow(new AmqpConnectException(new ConnectException("secret")));
        AdminOperationException result = assertThrows(AdminOperationException.class,
                () -> service.createQueue(new QueueRequest("demo.queue", true)));
        assertEquals(Failure.UNAVAILABLE, result.failure());
        assertNull(result.getCause());
        assertFalse(result.getMessage().contains("secret"));
    }

    @Test
    void ambiguousNullAndFalseResultsAreNotInvented404s() throws Exception {
        assertFailure(Failure.UNAVAILABLE, () -> service.createQueue(new QueueRequest("demo.queue", true)));
        useMockChannel();
        exchangeExists("demo.exchange");
        assertFailure(Failure.UNAVAILABLE, () -> service.deleteExchange("demo.exchange"));
    }

    @Test
    void detectsResourceDisappearingBetweenCheckAndMutation() throws Exception {
        useMockChannel();
        queueExists("demo.queue");
        doThrow(brokerError(404)).when(admin).deleteQueue("demo.queue", false, false);
        assertFailure(Failure.NOT_FOUND, () -> service.deleteQueue("demo.queue"));
    }

    private void useMockChannel() {
        when(template.execute(any())).thenAnswer(invocation ->
                ((ChannelCallback<?>) invocation.getArgument(0)).doInRabbit(channel));
    }

    private void queueExists(String name) throws IOException {
        when(channel.queueDeclarePassive(name)).thenReturn(
                new AMQP.Queue.DeclareOk.Builder().queue(name).messageCount(0).consumerCount(0).build());
    }

    private void exchangeExists(String name) throws IOException {
        when(channel.exchangeDeclarePassive(name)).thenReturn(new AMQP.Exchange.DeclareOk.Builder().build());
    }

    private static AmqpIOException brokerError(int code) {
        var reason = new AMQP.Channel.Close.Builder().replyCode(code).replyText("private broker details")
                .classId(50).methodId(10).build();
        return new AmqpIOException(new IOException(new ShutdownSignalException(false, false, reason, null)));
    }

    private static void assertFailure(Failure expected, Runnable operation) {
        assertEquals(expected, assertThrows(AdminOperationException.class, operation::run).failure());
    }
}
