package duocuc.cl.rodrigo.carniverocrud.messaging;

import duocuc.cl.rodrigo.carniverocrud.service.EmailService;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
public class NotificacionesListener {

    private static final Logger log = LoggerFactory.getLogger(NotificacionesListener.class);
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@<>]+@[^\\s@<>]+\\.[^\\s@<>]+$");
    private final EmailService emailService;
    private final RetryWait retryWait;

    @Autowired
    public NotificacionesListener(EmailService emailService) {
        this(emailService, Thread::sleep);
    }

    NotificacionesListener(EmailService emailService, RetryWait retryWait) {
        this.emailService = emailService;
        this.retryWait = retryWait;
    }

    @RabbitListener(queues = RabbitConfig.Q_CONFIRMACION)
    public void onPedidoConfirmado(PedidoConfirmadoEvent evento, Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        Integer compraId = evento == null ? null : evento.compraId();
        String estado = evento != null && estadoValido(evento.estado()) ? evento.estado() : "INVALIDO";
        log.info("Inicio notificación compraId={} estado={}", compraId, estado);
        String invalidReason = validar(evento);
        if (invalidReason != null) {
            rechazar(channel, deliveryTag, compraId, estado, invalidReason);
            return;
        }

        for (int attempt = 1; attempt <= 3; attempt++) {
            log.info("Intento SMTP={} compraId={} estado={}", attempt, compraId, estado);
            // Solo el envío está dentro de esta captura: nunca captura errores de ACK/NACK.
            RuntimeException sendFailure = null;
            try {
                emailService.enviarConfirmacionCompra(evento);
            } catch (RuntimeException ex) {
                sendFailure = ex;
            }
            if (sendFailure == null) {
                confirmar(channel, deliveryTag, compraId, estado);
                return;
            }
            boolean retryable = NotificationRetryPolicy.isRetryable(sendFailure);
            log.warn("Fallo SMTP compraId={} estado={} intento={} tipo={} recuperable={}",
                    compraId, estado, attempt, sendFailure.getClass().getSimpleName(), retryable);
            if (!retryable || attempt == 3) {
                rechazar(channel, deliveryTag, compraId, estado,
                        retryable ? "intentos_agotados" : "error_no_recuperable");
                return;
            }
            try {
                retryWait.pause(attempt * 1000L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                rechazar(channel, deliveryTag, compraId, estado, "espera_interrumpida");
                return;
            }
        }
    }

    private void confirmar(Channel channel, long tag, Integer compraId, String estado) throws IOException {
        try {
            channel.basicAck(tag, false);
        } catch (IOException | RuntimeException ex) {
            log.error("ACK falló compraId={} estado={} tipo={}; posible redelivery y correo duplicado",
                    compraId, estado, ex.getClass().getSimpleName());
            throw ex;
        }
        log.info("ACK enviado compraId={} estado={}", compraId, estado);
    }

    private void rechazar(Channel channel, long tag, Integer compraId, String estado, String reason)
            throws IOException {
        log.warn("Rechazo compraId={} estado={} motivo={}", compraId, estado, reason);
        try {
            channel.basicNack(tag, false, false);
        } catch (IOException | RuntimeException ex) {
            log.error("NACK falló compraId={} estado={} tipo={}; posible redelivery",
                    compraId, estado, ex.getClass().getSimpleName());
            throw ex;
        }
        log.warn("NACK enviado sin requeue compraId={} estado={} DLX={} DLQ={}",
                compraId, estado, RabbitConfig.DLX, RabbitConfig.Q_CONFIRMACION_DLQ);
    }

    private static boolean estadoValido(String estado) {
        return "PENDIENTE".equals(estado) || "COMPLETADA".equals(estado);
    }

    private static String validar(PedidoConfirmadoEvent event) {
        if (event == null) return "evento_ausente";
        if (event.compraId() == null || event.compraId() <= 0) return "compraId_invalido";
        if (event.emailCliente() == null || !EMAIL.matcher(event.emailCliente()).matches()) return "email_invalido";
        if (!estadoValido(event.estado())) return "estado_invalido";
        if (event.fecha() == null || event.fecha().isBlank()) return "fecha_ausente";
        if (event.total() < 0) return "total_invalido";
        if (event.items() == null || event.items().isEmpty()) return "items_ausentes";
        for (var item : event.items()) {
            if (item == null || item.planta() == null || item.planta().isBlank()
                    || item.cantidad() <= 0 || item.subtotal() < 0) return "item_invalido";
        }
        return null;
    }

    @FunctionalInterface
    interface RetryWait {
        void pause(long milliseconds) throws InterruptedException;
    }
}
