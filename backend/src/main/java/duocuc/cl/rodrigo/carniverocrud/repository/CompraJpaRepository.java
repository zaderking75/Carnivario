package duocuc.cl.rodrigo.carniverocrud.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import duocuc.cl.rodrigo.carniverocrud.models.entity.Compra;



@Repository 
public interface CompraJpaRepository extends JpaRepository<Compra,Integer> {

}
