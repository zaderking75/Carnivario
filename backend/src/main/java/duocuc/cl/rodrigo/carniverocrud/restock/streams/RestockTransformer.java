package duocuc.cl.rodrigo.carniverocrud.restock.streams;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import duocuc.cl.rodrigo.carniverocrud.restock.events.RestockEvent;
import duocuc.cl.rodrigo.carniverocrud.restock.events.RestockNotification;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.Transformer;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.state.KeyValueStore;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Mantiene una lista de espera por ID de planta dentro del state store de Kafka Streams. */
public class RestockTransformer implements Transformer<String, RestockEvent,
        Iterable<KeyValue<String, RestockNotification>>> {

    public static final String STORE_NAME = "restock-waitlist-store";
    private static final Duration REQUEST_TTL = Duration.ofDays(90);
    private final ObjectMapper mapper;
    private KeyValueStore<String, String> store;

    public RestockTransformer(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void init(ProcessorContext context) {
        this.store = (KeyValueStore<String, String>) context.getStateStore(STORE_NAME);
    }

    @Override
    public Iterable<KeyValue<String, RestockNotification>> transform(String plantKey, RestockEvent event) {
        if (plantKey == null || event == null || event.type() == null) return List.of();

        List<RestockEvent> waitlist = leerLista(plantKey);
        Instant cutoff = Instant.now().minus(REQUEST_TTL);
        waitlist.removeIf(r -> r.occurredAt() == null || r.occurredAt().isBefore(cutoff));
        List<KeyValue<String, RestockNotification>> output = new ArrayList<>();

        switch (event.type()) {
            case RestockEvent.REQUESTED -> {
                boolean alreadyWaiting = waitlist.stream()
                        .anyMatch(r -> r.userId().equals(event.userId()));
                if (!alreadyWaiting) waitlist.add(event);
            }
            case RestockEvent.CANCELLED ->
                    waitlist.removeIf(r -> r.userId().equals(event.userId()));
            case RestockEvent.LISTED -> {
                if (event.unitsAvailable() != null && event.unitsAvailable() > 0) {
                    waitlist.sort(Comparator.comparing(RestockEvent::occurredAt));
                    int winners = Math.min(event.unitsAvailable(), waitlist.size());
                    List<RestockEvent> selected = new ArrayList<>(waitlist.subList(0, winners));
                    for (RestockEvent request : selected) {
                        RestockNotification notification = RestockNotification.from(request, event);
                        output.add(KeyValue.pair(String.valueOf(request.userId()), notification));
                    }
                    waitlist.removeAll(selected);
                }
            }
            default -> { /* evento desconocido: se ignora */ }
        }

        guardarLista(plantKey, waitlist);
        return output;
    }

    private List<RestockEvent> leerLista(String key) {
        String json = store.get(key);
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return new ArrayList<>(mapper.readValue(json, new TypeReference<List<RestockEvent>>() {}));
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo leer la lista de espera de la planta " + key, e);
        }
    }

    private void guardarLista(String key, List<RestockEvent> waitlist) {
        try {
            if (waitlist.isEmpty()) store.delete(key);
            else store.put(key, mapper.writeValueAsString(waitlist));
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo guardar la lista de espera de la planta " + key, e);
        }
    }

    @Override
    public void close() { }
}
