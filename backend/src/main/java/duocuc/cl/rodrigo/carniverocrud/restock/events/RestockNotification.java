package duocuc.cl.rodrigo.carniverocrud.restock.events;

import java.time.Instant;
import java.util.UUID;

public record RestockNotification(
        String eventId,
        Integer userId,
        String email,
        Integer plantId,
        String plantName,
        Integer price,
        Integer unitsAvailable,
        Instant sentAt
) {
    public static RestockNotification from(RestockEvent request, RestockEvent listed) {
        return new RestockNotification(
                UUID.randomUUID().toString(),
                request.userId(), request.email(), listed.plantId(),
                listed.plantName(), listed.price(), listed.unitsAvailable(), Instant.now()
        );
    }
}
