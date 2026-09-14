package duocuc.cl.rodrigo.carniverocrud.models.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateProfileRequest {
    private String name;
    private String lastname;
    private String phone;
    private String address;
    private String commune;
}
