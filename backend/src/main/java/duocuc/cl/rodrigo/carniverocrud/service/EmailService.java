package duocuc.cl.rodrigo.carniverocrud.service;

import duocuc.cl.rodrigo.carniverocrud.messaging.PedidoConfirmadoEvent;
import duocuc.cl.rodrigo.carniverocrud.models.entity.Compra;
import duocuc.cl.rodrigo.carniverocrud.models.entity.Detalle_Compra;
import duocuc.cl.rodrigo.carniverocrud.models.entity.Planta;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import duocuc.cl.rodrigo.carniverocrud.messaging.PedidoConfirmadoEvent;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    public void enviarConfirmacionCompra(PedidoConfirmadoEvent e) {
    StringBuilder cuerpo = new StringBuilder();
    cuerpo.append("¡Gracias por tu compra en Carnivario!\n\n");
    cuerpo.append("Pedido N°: ").append(e.compraId()).append("\n");
    cuerpo.append("Fecha: ").append(e.fecha()).append("\n\n");
    cuerpo.append("Detalle:\n");
    for (PedidoConfirmadoEvent.Item item : e.items()) {
        cuerpo.append("- ").append(item.planta())
              .append(" x").append(item.cantidad())
              .append(" = $").append(item.subtotal()).append("\n");
    }
    cuerpo.append("\nTotal: $").append(e.total()).append("\n\n");
    cuerpo.append("Tu pedido está: ").append(e.estado()).append("\n\n");
    cuerpo.append("Gracias por preferir Carnivario 🌿");

    SimpleMailMessage mensaje = new SimpleMailMessage();
    mensaje.setTo(e.emailCliente());
    mensaje.setSubject("Confirmación de compra #" + e.compraId() + " - Carnivario");
    mensaje.setText(cuerpo.toString());

    mailSender.send(mensaje);
}
}