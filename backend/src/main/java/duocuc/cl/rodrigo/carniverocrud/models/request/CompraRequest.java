package duocuc.cl.rodrigo.carniverocrud.models.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@AllArgsConstructor
@Getter
@Setter
@NoArgsConstructor
public class CompraRequest {
    private String idUser;
    private Integer idPlanta;
    private Integer quantity;
    private String estado;
}
    
