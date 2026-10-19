package duocuc.cl.rodrigo.carniverocrud.messaging;

import duocuc.cl.rodrigo.carniverocrud.service.EmailService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class NotificacionesListener {

    private final EmailService emailService;

    public NotificacionesListener(EmailService emailService) {
        this.emailService = emailService;
    }

    @RabbitListener(queues = RabbitConfig.Q_CONFIRMACION)
    public void onPedidoConfirmado(PedidoConfirmadoEvent evento) {
        emailService.enviarConfirmacionCompra(evento);
    }
}