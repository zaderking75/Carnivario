package duocuc.cl.rodrigo.carniverocrud.controller.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class UsuarioResponse {
    private Integer id;
    private String name;
    private String lastname;
    private String email;
    private String role;
    private String phone;
    private String address;
    private String commune;
}