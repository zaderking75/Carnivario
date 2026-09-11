package duocuc.cl.rodrigo.carniverocrud.service;

import duocuc.cl.rodrigo.carniverocrud.controller.request.AuthRequest;
import duocuc.cl.rodrigo.carniverocrud.controller.request.RegisterRequest;
import duocuc.cl.rodrigo.carniverocrud.controller.security.JwtProvider;
import duocuc.cl.rodrigo.carniverocrud.repository.UsuarioDB;
import duocuc.cl.rodrigo.carniverocrud.repository.UsuarioJpaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

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

        // 2. Si la autenticación fue exitosa, la guarda en el contexto
        SecurityContextHolder.getContext().setAuthentication(authentication);

        UsuarioDB usuario = getUsuarioByEmail(request.getEmail());

        // 3. Genera el JWT usando email y rol
        return jwtProvider.generateToken(usuario.getEmail(), usuario.getRole());
    }

    public UsuarioDB registrarUsuario(RegisterRequest request) {
        return registrarUsuario(request, "CLIENTE");
    }

    public UsuarioDB registrarUsuario(RegisterRequest request, String role) {

        if (usuarioJpaRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new RuntimeException("El email ya está registrado");
        }

        // 1. Creamos la Entidad (UsuarioDB) y mapeamos los campos
        UsuarioDB usuario = new UsuarioDB();
        usuario.setName(request.getName());
        usuario.setLastname(request.getLastname());
        usuario.setEmail(request.getEmail());
        usuario.setPhone(request.getPhone());
        usuario.setAddress(request.getAddress());
        usuario.setCommune(request.getCommune());

        // 2. Encriptamos y guardamos la clave
        usuario.setPassword(passwordEncoder.encode(request.getPassword()));

        // 3. Establecer rol
        usuario.setRole(normalizeRole(role));

        return usuarioJpaRepository.save(usuario);
    }

    public List<UsuarioDB> getAllUsuarios() {
        return usuarioJpaRepository.findAll();
    }

    public UsuarioDB cambiarRol(Integer id, String role) {
        UsuarioDB usuario = getUsuarioById(id);
        usuario.setRole(normalizeRole(role));
        return usuarioJpaRepository.save(usuario);
    }

    public UsuarioDB getUsuarioById(Integer id) {
        return usuarioJpaRepository.findById(id).orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
    }
    public UsuarioDB getUsuarioByEmail(String email) {
        return usuarioJpaRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("Email no encontrado"));
    }

    private String normalizeRole(String role) {
        String normalizedRole = Optional.ofNullable(role)
                .orElse("CLIENTE")
                .trim()
                .toUpperCase();

        if (!normalizedRole.equals("CLIENTE") && !normalizedRole.equals("ADMIN")) {
            throw new RuntimeException("Rol inválido. Usa CLIENTE o ADMIN");
        }

        return normalizedRole;
    }

}
