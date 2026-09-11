package duocuc.cl.rodrigo.carniverocrud.models.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@Getter
@Setter
@NoArgsConstructor
public class RegisterRequest {
    private String name;
    private String lastname;
    private String email;
    private String password;
    private String phone;
    private String address;
    private String commune;
    private String role;
}
