package duocuc.cl.rodrigo.carniverocrud.messaging;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.*;

class RabbitConfigTest {

    @Test
    void configuresOnlyTheExistingConverterWithoutTopologyBeans() {
        try (var context = new AnnotationConfigApplicationContext(RabbitConfig.class)) {
            assertTrue(context.getBeansOfType(Queue.class).isEmpty());
            assertTrue(context.getBeansOfType(Exchange.class).isEmpty());
            assertTrue(context.getBeansOfType(Binding.class).isEmpty());
            assertTrue(context.getBeansOfType(Declarables.class).isEmpty());
            assertEquals(1, context.getBeansOfType(MessageConverter.class).size());
            var converter = assertInstanceOf(Jackson2JsonMessageConverter.class,
                    context.getBean("jsonMessageConverter", MessageConverter.class));
            assertEquals(Jackson2JavaTypeMapper.TypePrecedence.INFERRED,
                    converter.getJavaTypeMapper().getTypePrecedence());
        }
    }

    @Test
    void preservesNamesUsedByPublisherAndListener() {
        assertAll(
                () -> assertEquals("carnivario.eventos", RabbitConfig.EXCHANGE),
                () -> assertEquals("carnivario.dlx", RabbitConfig.DLX),
                () -> assertEquals("pedido.confirmado", RabbitConfig.RK_PEDIDO_CONFIRMADO),
                () -> assertEquals("notificaciones.confirmacion.queue", RabbitConfig.Q_CONFIRMACION),
                () -> assertEquals("notificaciones.confirmacion.dlq", RabbitConfig.Q_CONFIRMACION_DLQ));
    }

    @Test
    void preservesJsonFieldsAndRoundTripForTheExistingEvent() throws Exception {
        var converter = new RabbitConfig().jsonMessageConverter();
        PedidoConfirmadoEvent event = event();
        Message message = converter.toMessage(event, new MessageProperties());
        assertEquals(MessageProperties.CONTENT_TYPE_JSON, message.getMessageProperties().getContentType());
        var mapper = new ObjectMapper();
        assertEquals(mapper.readTree("""
                {"compraId":123,"emailCliente":"test@example.invalid","fecha":"20/10/2026 12:00",
                 "estado":"COMPLETADA","total":2000,
                 "items":[{"planta":"Drosera","cantidad":2,"subtotal":2000}]}
                """), mapper.readTree(message.getBody()));
        message.getMessageProperties().setInferredArgumentType(PedidoConfirmadoEvent.class);
        assertEquals(event, converter.fromMessage(message));
    }

    @Test
    void listenerInferredTypeTakesPrecedenceOverPublisherTypeHeader() {
        var converter = new RabbitConfig().jsonMessageConverter();
        Message message = converter.toMessage(event(), new MessageProperties());
        message.getMessageProperties().setHeader("__TypeId__", "java.lang.String");
        message.getMessageProperties().setInferredArgumentType(PedidoConfirmadoEvent.class);
        assertEquals(event(), converter.fromMessage(message));
    }

    private PedidoConfirmadoEvent event() {
        return new PedidoConfirmadoEvent(123, "test@example.invalid", "20/10/2026 12:00",
                "COMPLETADA", 2000, List.of(new PedidoConfirmadoEvent.Item("Drosera", 2, 2000)));
    }
}
