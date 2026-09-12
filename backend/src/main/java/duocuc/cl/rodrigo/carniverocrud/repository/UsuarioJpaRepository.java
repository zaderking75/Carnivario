package duocuc.cl.rodrigo.carniverocrud.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import duocuc.cl.rodrigo.carniverocrud.models.entity.Usuario;

import java.util.Optional;

@Repository
public interface UsuarioJpaRepository  extends JpaRepository<Usuario, Integer> {
    Optional<Usuario> findByEmail(String email);
    Optional<Usuario> findByEmailAndPassword(String email, String password);

}
