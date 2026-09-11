package duocuc.cl.rodrigo.carniverocrud;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@org.springframework.test.context.ActiveProfiles("test")
@AutoConfigureMockMvc
class CarniveroCrudApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @Transactional
    void registerFromFrontendCreatesClient() throws Exception {
        String email = UUID.randomUUID() + "@test.local";
        mockMvc.perform(post("/user/api/register")
                        .header("Origin", "http://localhost:5173")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Prueba","lastname":"Registro","email":"%s",
                                 "password":"prueba123","phone":"123456789",
                                 "address":"Calle 123","commune":"Santiago","role":"ADMIN"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(jsonPath("email").value(email))
                .andExpect(jsonPath("role").value("CLIENTE"))
                .andExpect(jsonPath("password").doesNotExist());
    }

    @Test
    void contextLoads() {
    }

}
