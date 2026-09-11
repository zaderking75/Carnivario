package duocuc.cl.rodrigo.carniverocrud.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import duocuc.cl.rodrigo.carniverocrud.models.entity.Role;

@Repository 
public interface RoleJpaRepository extends JpaRepository<Role, Integer> {
}
