package duocuc.cl.rodrigo.carniverocrud.models.entity;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
@Entity 
@Table (name = "planta")
public class Planta {
    @Id 
    @GeneratedValue (strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Integer id;
    @Column(name = "name", nullable = false,length = 50)
    private String name;
    @Column(name = "price", nullable = false)
    private int price;
    @Column(name = "image", nullable = false, length = 100)
    private String image;
    @Column(name = "size",nullable = false, length = 100)
    private String size;
    @Column(name = "planting", nullable = false, length = 100)
    private String  planting;
    @Column(name = "description", nullable = false, length = 200)
    private String description;
    @Column(name = "stock", nullable = false)
    private Integer stock;
}
