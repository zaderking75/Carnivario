package main.java.duocuc.cl.rodrigo.carniverocrud.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DetalleCompraJpaRepository extends JpaRepository<Detalle_Compra, Integer> {

}
