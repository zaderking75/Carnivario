package duocuc.cl.rodrigo.carniverocrud.restock.streams;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;

public class RestockJsonSerde<T> implements Serde<T> {
    private final ObjectMapper mapper;
    private final Class<T> type;

    public RestockJsonSerde(ObjectMapper mapper, Class<T> type) {
        this.mapper = mapper;
        this.type = type;
    }

    @Override
    public Serializer<T> serializer() {
        return (topic, data) -> {
            if (data == null) return null;
            try {
                return mapper.writeValueAsBytes(data);
            } catch (Exception e) {
                throw new IllegalStateException("No se pudo serializar evento de reposición", e);
            }
        };
    }

    @Override
    public Deserializer<T> deserializer() {
        return (topic, data) -> {
            if (data == null) return null;
            try {
                return mapper.readValue(data, type);
            } catch (Exception e) {
                throw new IllegalStateException("No se pudo deserializar evento de reposición", e);
            }
        };
    }
}
