package duocuc.cl.rodrigo.carniverocrud.messaging;

import java.util.List;

public record PedidoConfirmadoEvent(
        Integer compraId,
        String emailCliente,
        String fecha,
        String estado,
        int total,
        List<Item> items) {

    public record Item(String planta, int cantidad, int subtotal) {}
}