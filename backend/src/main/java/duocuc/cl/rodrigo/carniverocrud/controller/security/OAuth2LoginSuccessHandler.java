package duocuc.cl.rodrigo.carniverocrud.controller.security;

import duocuc.cl.rodrigo.carniverocrud.models.entity.Usuario;
import duocuc.cl.rodrigo.carniverocrud.repository.UsuarioJpaRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    @Autowired
    private UsuarioJpaRepository usuarioJpaRepository;

    @Autowired
    private JwtProvider jwtProvider;

    @Value("${azure.admin-group-id}")
    private String adminGroupId;

    private static final String FRONTEND_CALLBACK_URL = "http://localhost:5173/oauth-callback";

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                         Authentication authentication) throws IOException {

        OidcUser oidcUser = (OidcUser) authentication.getPrincipal();
        System.out.println("=== DEBUG OAUTH2 ===");
        System.out.println("Email: " + oidcUser.getEmail());
        System.out.println("Claims completos: " + oidcUser.getClaims());
        System.out.println("Groups claim: " + oidcUser.getAttribute("groups"));
        System.out.println("adminGroupId configurado: " + adminGroupId);
        System.out.println("====================");
        String email = oidcUser.getEmail();
        String nombre = oidcUser.getGivenName() != null ? oidcUser.getGivenName() : oidcUser.getFullName();
        List<String> groups = oidcUser.getAttribute("groups");
        boolean perteneceAlGrupoAdmin = groups != null && groups.contains(adminGroupId);
        String rolAsignado = perteneceAlGrupoAdmin ? "ADMIN" : "CLIENTE";     

        Usuario usuario = usuarioJpaRepository.findByEmail(email)
                .map(existingUser -> {
                    if (!existingUser.getRole().equals(rolAsignado)) {
                        existingUser.setRole(rolAsignado);
                        return usuarioJpaRepository.save(existingUser);
                    }
                    return existingUser;
                })
                .orElseGet(() -> crearUsuarioDesdeAzure(email, nombre, rolAsignado));

        String miToken = jwtProvider.generateToken(usuario.getEmail());

        response.sendRedirect(FRONTEND_CALLBACK_URL + "?token=" + miToken);
    }

    private Usuario crearUsuarioDesdeAzure(String email, String nombre, String rol) {
        Usuario nuevo = new Usuario();
        nuevo.setEmail(email);
        nuevo.setName(nombre != null ? nombre : "Usuario Microsoft");
        nuevo.setLastname("");
        nuevo.setRole(rol);
        // Password aleatoria e inutilizable: este usuario SOLO puede entrar vía Microsoft
        nuevo.setPassword(new BCryptPasswordEncoder().encode(UUID.randomUUID().toString()));
        nuevo.setPhone("");
        nuevo.setAddress("");
        nuevo.setCommune("");
        return usuarioJpaRepository.save(nuevo);
    }
}
