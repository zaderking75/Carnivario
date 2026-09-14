package duocuc.cl.rodrigo.carniverocrud.service;

import duocuc.cl.rodrigo.carniverocrud.models.entity.Compra;
import duocuc.cl.rodrigo.carniverocrud.models.entity.Detalle_Compra;
import duocuc.cl.rodrigo.carniverocrud.models.entity.Planta;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    public void enviarConfirmacionCompra(String emailDestino, Compra compra,
                                          List<Detalle_Compra> detalles, List<Planta> plantas, int total) {

        StringBuilder cuerpo = new StringBuilder();
        cuerpo.append("¡Gracias por tu compra en Carnivario!\n\n");
        cuerpo.append("Pedido N°: ").append(compra.getId()).append("\n");
        cuerpo.append("Fecha: ").append(
                compra.getPurchasedate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
        ).append("\n\n");
        cuerpo.append("Detalle:\n");

        for (int i = 0; i < detalles.size(); i++) {
            Detalle_Compra d = detalles.get(i);
            Planta p = plantas.get(i);
            int subtotal = d.getCantidad() * d.getPrecio();
            cuerpo.append("- ").append(p.getName())
                  .append(" x").append(d.getCantidad())
                  .append(" = $").append(subtotal).append("\n");
        }

        cuerpo.append("\nTotal: $").append(total).append("\n\n");
        cuerpo.append("Tu pedido está: ").append(compra.getEstado()).append("\n\n");
        cuerpo.append("Gracias por preferir Carnivario 🌿");

        SimpleMailMessage mensaje = new SimpleMailMessage();
        mensaje.setTo(emailDestino);
        mensaje.setSubject("Confirmación de compra #" + compra.getId() + " - Carnivario");
        mensaje.setText(cuerpo.toString());

        mailSender.send(mensaje);
    }
}