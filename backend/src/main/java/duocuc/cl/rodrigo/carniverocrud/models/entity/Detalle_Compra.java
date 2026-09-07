
@Getter
@Builder
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
@Entity
@Table(name = "detalle_compra")
public class Detalle_Compra {
    @Column(name = "id_detalle", updatable = false, nullable = false)
    private int id_detalle;
    @Column(name = "id_compra", updatable = false, nullable = false)
    private int id_compra;
    @Column(name = "id_planta", updatable = false, nullable = false)
    private int id_planta_;
    @Column(name = "cantidad", updatable = false, nullable = false)
    private int cantidad;
    @Column(name = "precio", updatable = false, nullable = false)
    private int precio;
    
}
