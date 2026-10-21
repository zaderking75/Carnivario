package duocuc.cl.rodrigo.carniverocrud.messaging;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import duocuc.cl.rodrigo.carniverocrud.models.entity.Compra;
import duocuc.cl.rodrigo.carniverocrud.models.entity.Detalle_Compra;
import duocuc.cl.rodrigo.carniverocrud.models.entity.Planta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EventPublisherTest {

    private RabbitTemplate rabbitTemplate;
    private EventPublisher publisher;

    @BeforeEach
    void setUp() {
        rabbitTemplate = mock(RabbitTemplate.class);
        publisher = new EventPublisher(rabbitTemplate);
    }

    @ParameterizedTest
    @CsvSource({"PENDIENTE,pedido.pendiente", "COMPLETADA,pedido.confirmado"})
    void preservesRoutingJsonAndPurchaseDataWithoutTransaction(String state, String routingKey) throws Exception {
        PurchaseFixture fixture = fixture(state);
        publish(state, fixture);
        assertEvent(capturedEvent(routingKey), state);
        assertPurchaseUnchanged(fixture, state);
        verifyNoMoreInteractions(rabbitTemplate);
    }

    @ParameterizedTest
    @CsvSource({"PENDIENTE,pedido.pendiente", "COMPLETADA,pedido.confirmado"})
    void publishesOnlyAfterCommitUsingValuesCapturedBeforeCallback(String state, String routingKey) throws Exception {
        PurchaseFixture fixture = fixture(state);
        new TransactionTemplate(new TestTransactionManager()).executeWithoutResult(status -> {
            publish(state, fixture);
            verifyNoInteractions(rabbitTemplate);
            assertPurchaseUnchanged(fixture, state);
            clearInvocations(fixture.entities());
            // El callback debe depender exclusivamente del evento ya construido.
            fixture.details().clear();
            fixture.plants().clear();
        });
        verifyNoInteractions(fixture.entities());
        assertEvent(capturedEvent(routingKey), state);
        verifyNoMoreInteractions(rabbitTemplate);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDIENTE", "COMPLETADA"})
    void rollbackNeverPublishes(String state) {
        PurchaseFixture fixture = fixture(state);
        new TransactionTemplate(new TestTransactionManager()).executeWithoutResult(status -> {
            publish(state, fixture);
            status.setRollbackOnly();
        });
        verifyNoInteractions(rabbitTemplate);
        assertPurchaseUnchanged(fixture, state);
    }

    @ParameterizedTest
    @CsvSource({"PENDIENTE,pedido.pendiente", "COMPLETADA,pedido.confirmado"})
    void preservesExistingPolicyWhenPublicationFailsAfterCommit(String state, String routingKey) {
        PurchaseFixture fixture = fixture(state);
        doThrow(new AmqpException("Fallo simulado de publicación")).when(rabbitTemplate)
                .convertAndSend(eq("carnivario.eventos"), eq(routingKey), any(PedidoConfirmadoEvent.class));
        assertDoesNotThrow(() -> new TransactionTemplate(new TestTransactionManager())
                .executeWithoutResult(status -> publish(state, fixture)));
        verify(rabbitTemplate).convertAndSend(eq("carnivario.eventos"), eq(routingKey), any(PedidoConfirmadoEvent.class));
        verifyNoMoreInteractions(rabbitTemplate);
        assertPurchaseUnchanged(fixture, state);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDIENTE", "COMPLETADA"})
    void preservesExistingPolicyWhenEventPreparationFails(String state) {
        PurchaseFixture fixture = fixture(state);
        fixture.plants().clear();
        assertDoesNotThrow(() -> publish(state, fixture));
        verifyNoInteractions(rabbitTemplate);
        assertPurchaseUnchanged(fixture, state);
    }

    private void publish(String state, PurchaseFixture fixture) {
        if ("PENDIENTE".equals(state)) {
            publisher.publicarPedidoPendiente("test@example.invalid", fixture.purchase(),
                    fixture.details(), fixture.plants(), 5500);
        } else {
            publisher.publicarPedidoConfirmado("test@example.invalid", fixture.purchase(),
                    fixture.details(), fixture.plants(), 5500);
        }
    }

    private PedidoConfirmadoEvent capturedEvent(String routingKey) {
        var event = ArgumentCaptor.forClass(PedidoConfirmadoEvent.class);
        verify(rabbitTemplate).convertAndSend(eq("carnivario.eventos"), eq(routingKey), event.capture());
        return event.getValue();
    }

    private void assertEvent(PedidoConfirmadoEvent event, String state) throws Exception {
        assertEquals(state, event.estado());
        var converter = new RabbitConfig().jsonMessageConverter();
        var message = converter.toMessage(event, new MessageProperties());
        var mapper = new ObjectMapper();
        assertEquals(mapper.readTree("""
                {"compraId":123,"emailCliente":"test@example.invalid","fecha":"20/10/2026 12:30",
                 "estado":"%s","total":5500,
                 "items":[{"planta":"Drosera","cantidad":2,"subtotal":3000},
                          {"planta":"Nepenthes","cantidad":1,"subtotal":2500}]}
                """.formatted(state)), mapper.readTree(message.getBody()));
        message.getMessageProperties().setInferredArgumentType(PedidoConfirmadoEvent.class);
        assertEquals(event, converter.fromMessage(message));
    }

    private PurchaseFixture fixture(String state) {
        Compra purchase = spy(Compra.builder().id(123).idUser("7")
                .purchasedate(LocalDateTime.of(2026, 10, 20, 12, 30)).estado(state).build());
        Detalle_Compra first = spy(Detalle_Compra.builder().id_compra(123).id_planta(1).cantidad(2).precio(1500).build());
        Detalle_Compra second = spy(Detalle_Compra.builder().id_compra(123).id_planta(2).cantidad(1).precio(2500).build());
        // Los precios actuales del catálogo no deben sustituir a los precios del detalle.
        Planta drosera = spy(Planta.builder().id(1).name("Drosera").price(9000).stock(10).build());
        Planta nepenthes = spy(Planta.builder().id(2).name("Nepenthes").price(8000).stock(20).build());
        return new PurchaseFixture(purchase, first, second, drosera, nepenthes,
                new ArrayList<>(List.of(first, second)), new ArrayList<>(List.of(drosera, nepenthes)));
    }

    private void assertPurchaseUnchanged(PurchaseFixture fixture, String state) {
        assertEquals(state, fixture.purchase().getEstado());
        assertEquals(123, fixture.purchase().getId());
        assertEquals("7", fixture.purchase().getIdUser());
        assertEquals(LocalDateTime.of(2026, 10, 20, 12, 30), fixture.purchase().getPurchasedate());
        assertEquals(2, fixture.first().getCantidad());
        assertEquals(1500, fixture.first().getPrecio());
        assertEquals(1, fixture.second().getCantidad());
        assertEquals(2500, fixture.second().getPrecio());
        assertEquals(10, fixture.drosera().getStock());
        assertEquals(20, fixture.nepenthes().getStock());
        for (Object entity : fixture.entities()) {
            assertTrue(mockingDetails(entity).getInvocations().stream()
                    .allMatch(invocation -> invocation.getMethod().getName().startsWith("get")),
                    "La publicación solo debe leer las entidades");
        }
    }

    private record PurchaseFixture(Compra purchase, Detalle_Compra first, Detalle_Compra second,
                                   Planta drosera, Planta nepenthes,
                                   List<Detalle_Compra> details, List<Planta> plants) {
        Object[] entities() {
            return new Object[]{purchase, first, second, drosera, nepenthes};
        }
    }

    /** Ejecuta los callbacks reales de Spring sin abrir una base de datos. */
    private static class TestTransactionManager extends AbstractPlatformTransactionManager {
        @Override
        protected Object doGetTransaction() { return new Object(); }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) { }

        @Override
        protected void doCommit(DefaultTransactionStatus status) { }

        @Override
        protected void doRollback(DefaultTransactionStatus status) { }
    }
}
