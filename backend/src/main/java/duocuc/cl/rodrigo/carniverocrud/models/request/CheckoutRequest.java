package duocuc.cl.rodrigo.carniverocrud.models.request;

import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
public class CheckoutRequest {
    private List<ItemCheckout> items;

    @Getter
    @Setter
    public static class ItemCheckout {
        private Integer idPlanta;
        private Integer cantidad;
    }
}
