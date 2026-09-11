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
@Table (name = "role")
public class Role { 
    @Id 
    @GeneratedValue (strategy = GenerationType.IDENTITY)
    @Column (name = "id_role", updatable = false, nullable = false)
    private Integer id_role;
    @Column(name = "name_role", nullable = false,length = 50)
    private String name_role;
}
