package duocuc.cl.rodrigo.carniverocrud.repository;


import duocuc.cl.rodrigo.carniverocrud.models.entity.Planta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PlantaJpaRepository extends JpaRepository<Planta, Integer> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from Planta p where p.id = :id")
    Optional<Planta> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Integer id);

    Optional<Planta> findByName(String name);
}
