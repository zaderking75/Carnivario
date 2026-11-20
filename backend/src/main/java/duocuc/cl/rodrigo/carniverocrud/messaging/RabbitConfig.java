
package duocuc.cl.rodrigo.carniverocrud.messaging;

import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "carnivario.eventos";
    public static final String DLX = "carnivario.dlx";

    public static final String RK_PEDIDO_CONFIRMADO = "pedido.confirmado";
    public static final String RK_PEDIDO_PENDIENTE = "pedido.pendiente";

    public static final String Q_CONFIRMACION =
            "notificaciones.confirmacion.queue";

    public static final String Q_CONFIRMACION_DLQ =
            "notificaciones.confirmacion.dlq";

    // Conversor JSON existente
    @Bean
    public MessageConverter jsonMessageConverter() {
        Jackson2JsonMessageConverter converter =
                new Jackson2JsonMessageConverter();

        DefaultJackson2JavaTypeMapper typeMapper =
                new DefaultJackson2JavaTypeMapper();

        typeMapper.setTypePrecedence(
                Jackson2JavaTypeMapper.TypePrecedence.INFERRED
        );

        converter.setJavaTypeMapper(typeMapper);

        return converter;
    }
}
