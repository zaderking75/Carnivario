package duocuc.cl.rodrigo.carniverocrud.restock.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import duocuc.cl.rodrigo.carniverocrud.restock.events.RestockNotification;
import duocuc.cl.rodrigo.carniverocrud.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class RestockNotificationListener {
    private static final Logger log = LoggerFactory.getLogger(RestockNotificationListener.class);
    private final ObjectMapper mapper;
    private final EmailService emailService;

    public RestockNotificationListener(ObjectMapper mapper, EmailService emailService) {
        this.mapper = mapper;
        this.emailService = emailService;
    }

    @KafkaListener(topics = "restock.notification.due",
            groupId = "carnivario-restock-email",
            containerFactory = "restockStringKafkaListenerContainerFactory")
    public void recibir(String json) throws Exception {
        RestockNotification notification = mapper.readValue(json, RestockNotification.class);
        emailService.enviarAvisoReposicion(notification);
        log.info("Aviso de reposición enviado. eventId={}, usuario={}, planta={}",
                notification.eventId(), notification.userId(), notification.plantId());
    }
}
