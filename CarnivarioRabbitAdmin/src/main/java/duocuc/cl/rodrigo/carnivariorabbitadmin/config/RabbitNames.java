package duocuc.cl.rodrigo.carnivariorabbitadmin.config;

public final class RabbitNames {

    public static final String EXCHANGE = "carnivario.eventos";
    public static final String DLX = "carnivario.dlx";
    public static final String Q_CONFIRMACION = "notificaciones.confirmacion.queue";
    public static final String Q_CONFIRMACION_DLQ = "notificaciones.confirmacion.dlq";
    public static final String RK_PEDIDO_CONFIRMADO = "pedido.confirmado";
    public static final String RK_PEDIDO_PENDIENTE = "pedido.pendiente";
    public static final String RK_DLQ = Q_CONFIRMACION_DLQ;

    private RabbitNames() {
    }
}
