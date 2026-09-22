package duocuc.cl.rodrigo.carniverocrud.models.request;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@Getter
@Setter
@NoArgsConstructor
public class ItemRequest {
    private Integer idPlanta;
    private Integer cantidad;
}
