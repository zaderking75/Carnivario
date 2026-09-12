package duocuc.cl.rodrigo.carniverocrud.models.request;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DetalleCompraRequest {
    private Integer idCompra;
    private Integer idPlanta;
    private Integer cantidad;
}
