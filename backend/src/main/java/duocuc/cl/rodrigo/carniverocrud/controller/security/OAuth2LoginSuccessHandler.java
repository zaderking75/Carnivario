package duocuc.cl.rodrigo.carniverocrud.controller.security;

import duocuc.cl.rodrigo.carniverocrud.models.entity.Usuario;
import duocuc.cl.rodrigo.carniverocrud.repository.UsuarioJpaRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    @Autowired
    private UsuarioJpaRepository usuarioJpaRepository;

    @Autowired
    private JwtProvider jwtProvider;

    private static final String FRONTEND_CALLBACK_URL = "http://localhost:5173/oauth-callback";

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                         Authentication authentication) throws IOException {

        OidcUser oidcUser = (OidcUser) authentication.getPrincipal();
        String email = oidcUser.getEmail();
        String nombre = oidcUser.getGivenName() != null ? oidcUser.getGivenName() : oidcUser.getFullName();

        Usuario usuario = usuarioJpaRepository.findByEmail(email)
                .orElseGet(() -> crearUsuarioDesdeAzure(email, nombre));

        String miToken = jwtProvider.generateToken(usuario.getEmail());

        response.sendRedirect(FRONTEND_CALLBACK_URL + "?token=" + miToken);
    }

    private Usuario crearUsuarioDesdeAzure(String email, String nombre) {
        Usuario nuevo = new Usuario();
        nuevo.setEmail(email);
        nuevo.setName(nombre != null ? nombre : "Usuario Microsoft");
        nuevo.setLastname("");
        nuevo.setRole("CLIENTE");
        // Password aleatoria e inutilizable: este usuario SOLO puede entrar vía Microsoft
        nuevo.setPassword(new BCryptPasswordEncoder().encode(UUID.randomUUID().toString()));
        nuevo.setPhone("");
        nuevo.setAddress("");
        nuevo.setCommune("");
        return usuarioJpaRepository.save(nuevo);
    }
}
