package duocuc.cl.rodrigo.carniverocrud.service;

import duocuc.cl.rodrigo.carniverocrud.controller.security.JwtProvider;
import duocuc.cl.rodrigo.carniverocrud.models.entity.Usuario;
import duocuc.cl.rodrigo.carniverocrud.models.request.AuthRequest;
import duocuc.cl.rodrigo.carniverocrud.models.request.RegisterRequest;
import duocuc.cl.rodrigo.carniverocrud.repository.UsuarioJpaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import duocuc.cl.rodrigo.carniverocrud.models.request.UpdateProfileRequest;

import java.util.List;
import java.util.Optional;

@Service
public class UsuarioService {
    @Autowired
    private UsuarioJpaRepository usuarioJpaRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtProvider jwtProvider;
    @Autowired
    private AuthenticationManager authenticationManager;

    public String login(AuthRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        
        Usuario usuario = getUsuarioByEmail(request.getEmail());

        // 3. Genera el JWT usando email y rol
        return jwtProvider.generateToken(usuario.getEmail());
    }
    public Usuario registrarUsuario(RegisterRequest request) {
        return registrarUsuario(request, "CLIENTE");
    }

    public Usuario registrarUsuario(RegisterRequest request, String role) {
        if (usuarioJpaRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new IllegalArgumentException("El email ya esta registrado");
        }

        // 1. Creamos la Entidad (Usuario) y mapeamos los campos
        Usuario usuario = new Usuario();
        usuario.setName(request.getName());
        usuario.setLastname(request.getLastname());
        usuario.setEmail(request.getEmail());
        usuario.setPhone(request.getPhone());
        usuario.setAddress(request.getAddress());
        usuario.setCommune(request.getCommune());
        usuario.setPassword(passwordEncoder.encode(request.getPassword()));
        usuario.setRole(normalizeRole(role));

        return usuarioJpaRepository.save(usuario);
    }
    public List<Usuario> getAllUsuarios() {
        return usuarioJpaRepository.findAll();
    }

    public Usuario cambiarRol(Integer id, String role) {
        Usuario usuario = getUsuarioById(id);
        usuario.setRole(normalizeRole(role));
        return usuarioJpaRepository.save(usuario);
    }
    public Usuario getUsuarioById(Integer id) {
        return usuarioJpaRepository.findById(id).orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
    }
    public Usuario getUsuarioByEmail(String email) {
        return usuarioJpaRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("Email no encontrado"));
    }
    private String normalizeRole(String role) {
        String normalizedRole = Optional.ofNullable(role)
                .orElse("CLIENTE")
                .trim()
                .toUpperCase();

        if (!normalizedRole.equals("CLIENTE") && !normalizedRole.equals("ADMIN")) {
            throw new IllegalArgumentException("Rol invalido. Usa CLIENTE o ADMIN");
        }

        return normalizedRole;
    }

    public Usuario actualizarPerfil(String email, UpdateProfileRequest request) {
    Usuario usuario = getUsuarioByEmail(email);

    usuario.setName(validarCampo(request.getName(), "nombre", 50));
    usuario.setLastname(validarCampo(request.getLastname(), "apellido", 50));
    usuario.setPhone(validarCampo(request.getPhone(), "teléfono", 50));
    usuario.setAddress(validarCampo(request.getAddress(), "dirección", 50));
    usuario.setCommune(validarCampo(request.getCommune(), "comuna", 50));

    return usuarioJpaRepository.save(usuario);
}

private String validarCampo(String valor, String nombreCampo, int largoMaximo) {
    if (valor == null || valor.trim().isEmpty()) {
        throw new IllegalArgumentException(
            "El campo " + nombreCampo + " es obligatorio"
        );
    }

    String valorLimpio = valor.trim();

    if (valorLimpio.length() > largoMaximo) {
        throw new IllegalArgumentException(
            "El campo " + nombreCampo +
            " no puede superar los " + largoMaximo + " caracteres"
        );
    }

    return valorLimpio;
}

}
