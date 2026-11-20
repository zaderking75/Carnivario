package duocuc.cl.rodrigo.carniverocrud.restock.events;

import java.time.Instant;

/** Evento interno que viaja por los tópicos de reposición. */
public record RestockEvent(
        String type,
        Integer userId,
        String email,
        Integer plantId,
        String plantName,
        Integer price,
        Integer unitsAvailable,
        Instant occurredAt
) {
    public static final String REQUESTED = "REQUESTED";
    public static final String CANCELLED = "CANCELLED";
    public static final String LISTED = "LISTED";

    public static RestockEvent requested(Integer userId, String email, Integer plantId,
                                         String plantName, Integer price) {
        return new RestockEvent(REQUESTED, userId, email, plantId, plantName,
                price, null, Instant.now());
    }

    public static RestockEvent cancelled(Integer userId, Integer plantId) {
        return new RestockEvent(CANCELLED, userId, null, plantId,
                null, null, null, Instant.now());
    }

    public static RestockEvent listed(Integer plantId, String plantName,
                                      Integer price, Integer unitsAvailable) {
        return new RestockEvent(LISTED, null, null, plantId, plantName,
                price, unitsAvailable, Instant.now());
    }
}
