package duocuc.cl.rodrigo.carniverocrud;

import duocuc.cl.rodrigo.carniverocrud.controller.security.JwtProvider;
import duocuc.cl.rodrigo.carniverocrud.models.entity.*;
import duocuc.cl.rodrigo.carniverocrud.models.request.CompraRequest;
import duocuc.cl.rodrigo.carniverocrud.repository.*;
import duocuc.cl.rodrigo.carniverocrud.service.CompraService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.List;
import java.util.Date;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PurchaseSecurityTests {
    @Autowired MockMvc mvc;
    @Autowired UsuarioJpaRepository users;
    @Autowired PlantaJpaRepository plants;
    @Autowired CompraJpaRepository purchases;
    @Autowired DetalleCompraJpaRepository details;
    @Autowired JwtProvider jwt;
    @Autowired CompraService service;
    Usuario owner;
    Usuario other;
    Usuario admin;
    Planta plant;

    @BeforeEach
    void setup() {
        details.deleteAll();
        purchases.deleteAll();
        plants.deleteAll();
        users.deleteAll();
        owner = user("owner@test.local", "CLIENTE");
        other = user("other@test.local", "CLIENTE");
        admin = user("admin@test.local", "ADMIN");
        plant = plants.save(Planta.builder().name("Drosera").price(1000).stock(2)
                .image("/images/test.jpg").size("S").planting("Primavera").description("Test").build());
    }

    Usuario user(String email, String role) {
        return users.save(Usuario.builder().name("Test").lastname("User").email(email)
                .password("unused").role(role).phone("123").address("Calle 1").commune("Santiago").build());
    }

    String token(Usuario user) { return "Bearer " + jwt.generateToken(user.getEmail(), user.getRole()); }

    Compra purchase() {
        Compra compra = new Compra();
        compra.setIdUser(owner.getId().toString());
        compra.setEstado("PENDIENTE");
        return purchases.save(compra);
    }

    @Test void publicEndpointAndMissingOrInvalidTokens() throws Exception {
        mvc.perform(get("/planta/api")).andExpect(status().isOk());
        mvc.perform(get("/purchase/api")).andExpect(status().isUnauthorized());
        mvc.perform(post("/purchase/api").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/purchase/api").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
        String expired = io.jsonwebtoken.Jwts.builder().setSubject(owner.getEmail())
                .setExpiration(new Date(System.currentTimeMillis() - 10000))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(new byte[64]), io.jsonwebtoken.SignatureAlgorithm.HS512)
                .compact();
        mvc.perform(get("/purchase/api").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());
        byte[] wrongKey = new byte[64];
        java.util.Arrays.fill(wrongKey, (byte) 1);
        String forged = io.jsonwebtoken.Jwts.builder().setSubject(admin.getEmail())
                .setExpiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(wrongKey), io.jsonwebtoken.SignatureAlgorithm.HS512)
                .compact();
        mvc.perform(get("/purchase/api").header("Authorization", "Bearer " + forged))
                .andExpect(status().isUnauthorized());
    }

    @Test void globalPurchasesRequireAdmin() throws Exception {
        mvc.perform(get("/purchase/api").header("Authorization", token(owner))).andExpect(status().isForbidden());
        mvc.perform(get("/purchase/api").header("Authorization", token(admin))).andExpect(status().isOk());
    }

    @Test void purchaseOnlyVisibleToOwnerOrAdmin() throws Exception {
        int id = purchase().getId();
        mvc.perform(get("/purchase/api/" + id).header("Authorization", token(other))).andExpect(status().isForbidden());
        mvc.perform(get("/purchase/api/" + id).header("Authorization", token(owner))).andExpect(status().isOk());
        mvc.perform(get("/purchase/api/" + id).header("Authorization", token(admin))).andExpect(status().isOk());
    }

    @Test void serverControlsBuyerAndInitialState() throws Exception {
        mvc.perform(post("/purchase/api").header("Authorization", token(owner)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPlanta\":" + plant.getId() + ",\"quantity\":1,\"idUser\":\"" + other.getId() + "\",\"estado\":\"PAGADA\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("idUser").value(owner.getId().toString()))
                .andExpect(jsonPath("estado").value("PENDIENTE"));
        assertEquals(1, plants.findById(plant.getId()).orElseThrow().getStock());
        assertEquals(1, details.count());
    }

    @Test void incompletePurchaseAndInsufficientStockDoNotPersist() throws Exception {
        for (String body : List.of("{}", "{\"quantity\":1}",
                "{\"idPlanta\":" + plant.getId() + ",\"quantity\":0}",
                "{\"idPlanta\":" + plant.getId() + ",\"quantity\":3}")) {
            mvc.perform(post("/purchase/api").header("Authorization", token(owner))
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        assertEquals(0, purchases.count());
        assertEquals(0, details.count());
        assertEquals(2, plants.findById(plant.getId()).orElseThrow().getStock());
    }

    @Test void missingResourcesReturn404() throws Exception {
        mvc.perform(get("/purchase/api/2147483647").header("Authorization", token(owner))).andExpect(status().isNotFound());
        mvc.perform(get("/planta/api/2147483647")).andExpect(status().isNotFound());
        mvc.perform(post("/purchase/api").header("Authorization", token(owner)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPlanta\":2147483647,\"quantity\":1}")).andExpect(status().isNotFound());
    }

    @Test void deletedUserTokenIsUnauthorized() throws Exception {
        String bearer = token(owner);
        users.delete(owner);
        mvc.perform(get("/purchase/api").header("Authorization", bearer)).andExpect(status().isUnauthorized());
    }

    @Test void simultaneousPurchasesCannotOversell() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        var authentication = new UsernamePasswordAuthenticationToken(owner.getEmail(), null,
                List.of(new SimpleGrantedAuthority("ROLE_CLIENTE")));
        Callable<Boolean> buy = () -> {
            start.await();
            try {
                service.registerPurchase(new CompraRequest(null, plant.getId(), 2, null), authentication);
                return true;
            } catch (IllegalArgumentException exception) {
                assertEquals("Stock insuficiente.", exception.getMessage());
                return false;
            }
        };
        try {
            var first = executor.submit(buy);
            var second = executor.submit(buy);
            start.countDown();
            assertNotEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertEquals(0, plants.findById(plant.getId()).orElseThrow().getStock());
            assertEquals(1, purchases.count());
            assertEquals(1, details.count());
        } finally {
            executor.shutdownNow();
        }
    }
}
