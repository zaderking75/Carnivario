package duocuc.cl.rodrigo.carniverocrud;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
class CarniveroCrudApplicationTests extends IsolatedAuthTest {

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
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.role").value("CLIENTE"))
                .andExpect(jsonPath("$.user.password").exists());
    }

    @Test
    void contextLoads() {
    }

}
