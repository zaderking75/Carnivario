
import lombok.*;
@Getter
@Builder
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
@Entity
@Table(name = "role")
public class Role { 
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id_role;
    @Column(name = "name_role", nullable = false,length = 50)
    private String name_role;
}
