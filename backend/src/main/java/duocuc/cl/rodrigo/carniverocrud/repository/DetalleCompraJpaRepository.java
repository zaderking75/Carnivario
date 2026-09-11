package duocuc.cl.rodrigo.carniverocrud.repository    ;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import duocuc.cl.rodrigo.carniverocrud.models.entity.Detalle_Compra;

@Repository
public interface DetalleCompraJpaRepository extends JpaRepository<Detalle_Compra, Integer> {

}
