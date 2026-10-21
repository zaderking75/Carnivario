package duocuc.cl.rodrigo.carnivariorabbitadmin.service;

import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.ShutdownSignalException;
import duocuc.cl.rodrigo.carnivariorabbitadmin.config.RabbitNames;
import duocuc.cl.rodrigo.carnivariorabbitadmin.dto.BindingRequest;
import duocuc.cl.rodrigo.carnivariorabbitadmin.dto.ExchangeRequest;
import duocuc.cl.rodrigo.carnivariorabbitadmin.dto.QueueRequest;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RabbitAdminService {

    private static final Set<String> PROTECTED_NAMES = Set.of(
            RabbitNames.EXCHANGE, RabbitNames.DLX,
            RabbitNames.Q_CONFIRMACION, RabbitNames.Q_CONFIRMACION_DLQ);

    private final AmqpAdmin admin;
    private final RabbitTemplate rabbitTemplate;
    private final Validator validator;

    public void createQueue(QueueRequest request) {
        validate(request);
        requireDemo(request.name());
        broker(() -> {
            if (admin.declareQueue(new Queue(request.name(), request.durable(), false, false)) == null) {
                throw new AdminOperationException(Failure.UNAVAILABLE);
            }
            return null;
        });
    }

    public void deleteQueue(String name) {
        requireDemo(name);
        broker(() -> {
            queueState(name);
            // Esta sobrecarga propaga errores; la variante booleana puede ocultar IOException.
            admin.deleteQueue(name, false, false);
            return null;
        });
    }

    public QueueState getQueue(String name) {
        requireDemo(name);
        return broker(() -> queueState(name));
    }

    public void createExchange(ExchangeRequest request) {
        validate(request);
        requireDemo(request.name());
        Exchange exchange = switch (request.type()) {
            case "direct" -> ExchangeBuilder.directExchange(request.name()).durable(request.durable()).build();
            case "topic" -> ExchangeBuilder.topicExchange(request.name()).durable(request.durable()).build();
            case "fanout" -> ExchangeBuilder.fanoutExchange(request.name()).durable(request.durable()).build();
            default -> throw new AdminOperationException(Failure.INVALID);
        };
        broker(() -> {
            admin.declareExchange(exchange);
            return null;
        });
    }

    public void deleteExchange(String name) {
        requireDemo(name);
        broker(() -> {
            requireExchange(name);
            if (!admin.deleteExchange(name)) {
                // false no demuestra que el exchange no exista.
                throw new AdminOperationException(Failure.UNAVAILABLE);
            }
            return null;
        });
    }

    public void createBinding(BindingRequest request) {
        validateBinding(request);
        broker(() -> {
            requireBindingResources(request);
            admin.declareBinding(binding(request));
            return null;
        });
    }

    public void deleteBinding(BindingRequest request) {
        validateBinding(request);
        broker(() -> {
            requireBindingResources(request);
            // AMQP no ofrece una consulta de existencia de bindings: eliminación idempotente.
            admin.removeBinding(binding(request));
            return null;
        });
    }

    private void validateBinding(BindingRequest request) {
        validate(request);
        requireDemo(request.exchange());
        requireDemo(request.queue());
    }

    private void requireBindingResources(BindingRequest request) {
        queueState(request.queue());
        requireExchange(request.exchange());
    }

    private Binding binding(BindingRequest request) {
        return new Binding(request.queue(), Binding.DestinationType.QUEUE,
                request.exchange(), request.routingKey(), Map.of());
    }

    private QueueState queueState(String name) {
        QueueState state = rabbitTemplate.execute(channel -> {
            AMQP.Queue.DeclareOk result = channel.queueDeclarePassive(name);
            return new QueueState(result.getQueue(), result.getMessageCount(), result.getConsumerCount());
        });
        if (state == null) {
            throw new AdminOperationException(Failure.UNAVAILABLE);
        }
        return state;
    }

    private void requireExchange(String name) {
        AMQP.Exchange.DeclareOk result = rabbitTemplate.execute(channel -> channel.exchangeDeclarePassive(name));
        if (result == null) {
            throw new AdminOperationException(Failure.UNAVAILABLE);
        }
    }

    private void validate(Object request) {
        if (request == null || !validator.validate(request).isEmpty()) {
            throw new AdminOperationException(Failure.INVALID);
        }
    }

    private void requireDemo(String name) {
        if (name == null || name.length() > 255 || !name.matches(QueueRequest.NAME_PATTERN)) {
            throw new AdminOperationException(Failure.INVALID);
        }
        if (PROTECTED_NAMES.contains(name) || !name.startsWith("demo.")) {
            throw new AdminOperationException(Failure.FORBIDDEN);
        }
    }

    private <T> T broker(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (AmqpException | ShutdownSignalException exception) {
            throw new AdminOperationException(classify(exception));
        }
    }

    private Failure classify(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ShutdownSignalException shutdown
                    && shutdown.getReason() instanceof AMQP.Channel.Close close) {
                return switch (close.getReplyCode()) {
                    case 404 -> Failure.NOT_FOUND;
                    case 403 -> Failure.FORBIDDEN;
                    case 405, 406 -> Failure.CONFLICT;
                    default -> Failure.UNAVAILABLE;
                };
            }
        }
        return Failure.UNAVAILABLE;
    }

    public record QueueState(String name, long messageCount, int consumerCount) { }

    public enum Failure { INVALID, FORBIDDEN, NOT_FOUND, CONFLICT, UNAVAILABLE }

    public static final class AdminOperationException extends RuntimeException {
        private final Failure failure;

        public AdminOperationException(Failure failure) {
            super(failure.name());
            this.failure = failure;
        }

        public Failure failure() {
            return failure;
        }
    }
}
