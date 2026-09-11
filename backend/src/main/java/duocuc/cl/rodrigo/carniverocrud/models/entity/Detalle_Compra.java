package duocuc.cl.rodrigo.carniverocrud.models.entity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;
@Getter
@Builder
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
@Entity 
@Table (name = "detalle_compra")
public class Detalle_Compra {
    @Id 
    @GeneratedValue (strategy = GenerationType.IDENTITY)
    @Column (name = "id_detalle", updatable = false, nullable = false)
    private int id_detalle;
    @Column(name = "id_compra", updatable = false, nullable = false)
    private int id_compra;
    @Column(name = "id_planta", updatable = false, nullable = false)
    private int id_planta;
    @Column(name = "cantidad", updatable = false, nullable = false)
    private int cantidad;
    @Column(name = "precio", updatable = false, nullable = false)
    private int precio;
    
}
