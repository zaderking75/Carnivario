package duocuc.cl.rodrigo.carniverocrud.models.entity;

import lombok.*;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
@Getter
@Builder
@Setter
@AllArgsConstructor
@ToString
@Entity
@Table(name = "compra")
public class Compra {
  @Id
  @GeneratedValue (strategy = GenerationType.IDENTITY)
  @Column (name = "id_purchase", updatable = false, nullable = false)
  private Integer id;
  @Column(name = "id_user", nullable = false,length = 50)
  private String idUser;
  @Column(name = "purchase_date")
  private LocalDateTime purchasedate;
  public Compra() {
      this.purchasedate = LocalDateTime.now();
  }
  @Column(name = "estado", nullable = false,length = 50)
  private String estado;
}
