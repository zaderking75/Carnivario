package duocuc.cl.rodrigo.carniverocrud.restock.config;

import com.fasterxml.jackson.databind.ObjectMapper;

import duocuc.cl.rodrigo.carniverocrud.restock.events.RestockEvent;
import duocuc.cl.rodrigo.carniverocrud.restock.events.RestockNotification;
import duocuc.cl.rodrigo.carniverocrud.restock.producer.RestockEventPublisher;
import duocuc.cl.rodrigo.carniverocrud.restock.streams.RestockJsonSerde;
import duocuc.cl.rodrigo.carniverocrud.restock.streams.RestockTransformer;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Named;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.Stores;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaStreams;
import org.springframework.kafka.config.KafkaStreamsConfiguration;

import java.util.HashMap;
import java.util.Map;

import org.springframework.kafka.annotation.KafkaStreamsDefaultConfiguration;

@Configuration
@EnableKafkaStreams
public class RestockKafkaConfig {

    @Bean(name = KafkaStreamsDefaultConfiguration.DEFAULT_STREAMS_CONFIG_BEAN_NAME)
    public KafkaStreamsConfiguration kafkaStreamsConfiguration(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        Map<String, Object> props = new HashMap<>();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "carnivario-restock-matcher");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.StringSerde.class);
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.StringSerde.class);
        props.put(StreamsConfig.PROCESSING_GUARANTEE_CONFIG, StreamsConfig.AT_LEAST_ONCE);
        props.put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, 1000);
        return new KafkaStreamsConfiguration(props);
    }

    @Bean
    public KStream<String, RestockNotification> restockTopology(
            StreamsBuilder builder, ObjectMapper mapper) {

        builder.addStateStore(Stores.keyValueStoreBuilder(
                Stores.persistentKeyValueStore(RestockTransformer.STORE_NAME),
                Serdes.String(), Serdes.String()));

        var eventSerde = new RestockJsonSerde<>(mapper, RestockEvent.class);
        var notificationSerde = new RestockJsonSerde<>(mapper, RestockNotification.class);

        KStream<String, RestockEvent> requested = builder.stream(
                RestockEventPublisher.REQUESTED_TOPIC,
                Consumed.with(Serdes.String(), eventSerde));
        KStream<String, RestockEvent> cancelled = builder.stream(
                RestockEventPublisher.CANCELLED_TOPIC,
                Consumed.with(Serdes.String(), eventSerde));
        KStream<String, RestockEvent> listed = builder.stream(
                RestockEventPublisher.LISTED_TOPIC,
                Consumed.with(Serdes.String(), eventSerde));

        KStream<String, RestockNotification> notifications = requested
                .merge(cancelled)
                .merge(listed)
                .flatTransform(() -> new RestockTransformer(mapper),
                        Named.as("restock-waitlist-processor"), RestockTransformer.STORE_NAME);

        notifications.to(RestockEventPublisher.NOTIFICATION_TOPIC,
                Produced.with(Serdes.String(), notificationSerde));
        return notifications;
    }
}
