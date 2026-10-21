package duocuc.cl.rodrigo.carniverocrud.service;

import java.util.List;

import duocuc.cl.rodrigo.carniverocrud.messaging.PedidoConfirmadoEvent;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock private JavaMailSender mailSender;
    @InjectMocks private EmailService service;

    @ParameterizedTest
    @CsvSource({"PENDIENTE,Pedido recibido #123 - Carnivario",
            "COMPLETADA,Confirmación de compra #123 - Carnivario"})
    void selectsSubjectAndBodyWithoutChangingRecipientOrPurchaseDetails(String state, String subject) {
        PedidoConfirmadoEvent event = event(state);
        service.enviarConfirmacionCompra(event);

        var captured = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captured.capture());
        verifyNoMoreInteractions(mailSender);
        SimpleMailMessage message = captured.getValue();
        assertArrayEquals(new String[]{"test@example.invalid"}, message.getTo());
        assertEquals(subject, message.getSubject());
        String body = message.getText();
        assertNotNull(body);
        assertTrue(body.contains("Pedido N°: 123"));
        assertTrue(body.contains("Fecha: 20/10/2026 12:30"));
        assertTrue(body.contains("- Drosera x2 = $3000"));
        assertTrue(body.contains("- Nepenthes x1 = $2500"));
        assertTrue(body.contains("Total: $5500"));
        assertTrue(body.contains("Tu pedido está: " + state));
        if ("PENDIENTE".equals(state)) {
            assertTrue(body.contains("Está pendiente y todavía no está confirmado."));
            assertFalse(body.contains("compra está confirmada"));
            assertFalse(body.toLowerCase(java.util.Locale.ROOT).contains("pagado"));
        } else {
            assertTrue(body.contains("Tu compra está confirmada y completada."));
            assertFalse(body.contains("todavía no está confirmado"));
        }
        assertEquals(event(state), event);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"CANCELADA", "CONFIRMADA", "pendiente", " COMPLETADA "})
    void neverSendsAConfirmationForUnknownStates(String state) {
        assertThrows(IllegalArgumentException.class, () -> service.enviarConfirmacionCompra(event(state)));
        verifyNoInteractions(mailSender);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDIENTE", "COMPLETADA"})
    void preservesPropagationOfMailFailures(String state) {
        var failure = new MailSendException("Fallo SMTP simulado");
        doThrow(failure).when(mailSender).send(any(SimpleMailMessage.class));
        assertSame(failure, assertThrows(MailSendException.class,
                () -> service.enviarConfirmacionCompra(event(state))));
        verify(mailSender).send(any(SimpleMailMessage.class));
        verifyNoMoreInteractions(mailSender);
    }

    private PedidoConfirmadoEvent event(String state) {
        return new PedidoConfirmadoEvent(123, "test@example.invalid", "20/10/2026 12:30", state, 5500,
                List.of(new PedidoConfirmadoEvent.Item("Drosera", 2, 3000),
                        new PedidoConfirmadoEvent.Item("Nepenthes", 1, 2500)));
    }
}
