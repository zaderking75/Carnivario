package duocuc.cl.rodrigo.carniverocrud.models.request;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@AllArgsConstructor
@Getter
@Setter
@NoArgsConstructor
public class CompraRequest {
    private List<ItemRequest> items;
}
    
