package duocuc.cl.rodrigo.carniverocrud;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import duocuc.cl.rodrigo.carniverocrud.models.entity.Usuario;
import duocuc.cl.rodrigo.carniverocrud.repository.UsuarioJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class HybridAuthenticationTests extends IsolatedAuthTest {
    @Autowired MockMvc mvc;
    @Autowired UsuarioJpaRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper json;

    private String localLogin(String role) throws Exception {
        String email = UUID.randomUUID() + "@test.local";
        users.save(Usuario.builder().email(email).name("Local").lastname("Test")
                .password(encoder.encode("local-test-password")).role(role).phone("").address("").commune("").build());
        String response = mvc.perform(post("/user/api/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.createObjectNode().put("email", email).put("password", "local-test-password").toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("user.role").value(role))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("token").asText();
    }

    @Test void publicCatalogAndPreflightWork() throws Exception {
        mvc.perform(get("/planta/api")).andExpect(status().isOk());
        mvc.perform(options("/user/api/me").header("Origin", "https://3-212-230-250.sslip.io")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "Authorization,Content-Type,X-Local-Token"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://3-212-230-250.sslip.io"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
    }

    @Test void protectedRoutesRejectAnonymousRequests() throws Exception {
        mvc.perform(get("/user/api/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/purchase/api").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/auth/azure-check")).andExpect(status().isUnauthorized());
        mvc.perform(post("/uploads/api")).andExpect(status().isUnauthorized());
    }

    @Test void localLoginUsesLocalHeaderAndCannotAccessAdminOrAzureRoutes() throws Exception {
        String token = localLogin("CLIENTE");
        mvc.perform(get("/user/api/me").header("X-Local-Token", token))
                .andExpect(status().isOk()).andExpect(jsonPath("role").value("CLIENTE"));
        mvc.perform(get("/user/api").header("X-Local-Token", token)).andExpect(status().isForbidden());
        mvc.perform(post("/planta/api").header("X-Local-Token", token)).andExpect(status().isForbidden());
        mvc.perform(get("/auth/azure-check").header("X-Local-Token", token)).andExpect(status().isForbidden());
        mvc.perform(get("/user/api/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test void localAdminRetainsAdminAccessButCannotMasqueradeAsAzure() throws Exception {
        String token = localLogin("ADMIN");
        mvc.perform(get("/user/api").header("X-Local-Token", token)).andExpect(status().isOk());
        mvc.perform(post("/auth/azure-admin-check").header("X-Local-Token", token))
                .andExpect(status().isForbidden());
    }

    @Test void badLocalCredentialsAndMalformedTokensReturn401() throws Exception {
        mvc.perform(post("/user/api/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"missing@test.local\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/user/api/me").header("X-Local-Token", "invalid")).andExpect(status().isUnauthorized());
        mvc.perform(get("/user/api/me").header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
    }

    @Test void microsoftProfileIsCreatedOnceAndRoleIsSynchronized() throws Exception {
        String email = "new-microsoft@test.local";
        String token = azureToken(email, "CLIENTE");
        mvc.perform(get("/user/api/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("email").value(email))
                .andExpect(jsonPath("role").value("CLIENTE")).andExpect(jsonPath("password").doesNotExist());
        Usuario user = users.findByEmail(email).orElseThrow();
        Integer id = user.getId();
        String password = user.getPassword();
        mvc.perform(get("/user/api/me").header("Authorization", "Bearer " + azureToken(email, "ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("id").value(id))
                .andExpect(jsonPath("role").value("ADMIN"));
        assertEquals(password, users.findByEmail(email).orElseThrow().getPassword());
        mvc.perform(get("/user/api/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("role").value("CLIENTE"));
    }

    @Test void azureScopesAndRolesAreBothRequired() throws Exception {
        String client = azureToken("client@test.local", "CLIENTE");
        mvc.perform(get("/auth/azure-check").header("Authorization", "Bearer " + client))
                .andExpect(status().isOk()).andExpect(jsonPath("iss").value(ISSUER));
        mvc.perform(post("/auth/azure-admin-check").header("Authorization", "Bearer " + client))
                .andExpect(status().isForbidden());
        mvc.perform(post("/auth/azure-admin-check").header("Authorization", "Bearer " + azureToken("admin@test.local", "ADMIN")))
                .andExpect(status().isOk());
        String noWrite = azureToken("admin@test.local", "ADMIN", "Carnivario.Read", ISSUER, AUDIENCE,
                Instant.now().plusSeconds(300), SIGNING_KEY, "preferred_username");
        mvc.perform(post("/auth/azure-admin-check").header("Authorization", "Bearer " + noWrite))
                .andExpect(status().isForbidden());
    }

    @Test void azureValidationRejectsWrongAudienceIssuerExpiryAndSignature() throws Exception {
        Instant future = Instant.now().plusSeconds(300);
        String[] invalid = {
                azureToken("user@test.local", "ADMIN", "Carnivario.Read", ISSUER, "another-api", future, SIGNING_KEY, "preferred_username"),
                azureToken("user@test.local", "ADMIN", "Carnivario.Read", "https://another-issuer.invalid", AUDIENCE, future, SIGNING_KEY, "preferred_username"),
                azureToken("user@test.local", "ADMIN", "Carnivario.Read", ISSUER, AUDIENCE, Instant.now().minusSeconds(300), SIGNING_KEY, "preferred_username"),
                azureToken("user@test.local", "ADMIN", "Carnivario.Read", ISSUER, AUDIENCE, future, new RSAKeyGenerator(2048).generate(), "preferred_username")
        };
        for (String token : invalid) {
            mvc.perform(get("/user/api/me").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test void tokenTypesCannotBeCombinedOrSwapped() throws Exception {
        String local = localLogin("ADMIN");
        String azure = azureToken("client@test.local", "CLIENTE");
        mvc.perform(get("/user/api/me").header("X-Local-Token", local).header("Authorization", "Bearer " + azure))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/user/api/me").header("X-Local-Token", azure)).andExpect(status().isUnauthorized());
    }

    @Test void emailFallbackWorksAndMissingEmailIsRejected() throws Exception {
        String fallback = azureToken("fallback@test.local", "CLIENTE", "Carnivario.Read", ISSUER, AUDIENCE,
                Instant.now().plusSeconds(300), SIGNING_KEY, "email");
        mvc.perform(get("/user/api/me").header("Authorization", "Bearer " + fallback))
                .andExpect(status().isOk()).andExpect(jsonPath("email").value("fallback@test.local"));
        String missing = azureToken("no-email", "CLIENTE", "Carnivario.Read", ISSUER, AUDIENCE,
                Instant.now().plusSeconds(300), SIGNING_KEY, "other_claim");
        mvc.perform(get("/user/api/me").header("Authorization", "Bearer " + missing))
                .andExpect(status().isUnauthorized());
    }
}
