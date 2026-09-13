package duocuc.cl.rodrigo.carniverocrud.controller;

import duocuc.cl.rodrigo.carniverocrud.controller.response.UsuarioResponse;
import duocuc.cl.rodrigo.carniverocrud.models.entity.Usuario;
import duocuc.cl.rodrigo.carniverocrud.models.request.AuthRequest;
import duocuc.cl.rodrigo.carniverocrud.models.request.RegisterRequest;
import duocuc.cl.rodrigo.carniverocrud.service.UsuarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/user/api")
public class UserController {
    @Autowired
    private UsuarioService usuarioService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequest loginRequest) {
        try {
            String jwtToken = usuarioService.login(loginRequest);
            Usuario usuario = usuarioService.getUsuarioByEmail(loginRequest.getEmail());

            Map<String, Object> response = new HashMap<>();
            response.put("token", jwtToken);
            response.put("user", mapToResponse(usuario));
            response.put("message", "Login exitoso");
            return ResponseEntity.ok(response);

        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Collections.singletonMap("error", "Credenciales incorrectas o usuario no encontrado."));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("error", "Error de autenticacion: " + e.getMessage()));
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        try {
            Usuario nuevoUsuario = usuarioService.registrarUsuario(request);
            return new ResponseEntity<>(mapToResponse(nuevoUsuario), HttpStatus.CREATED);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping
    public ResponseEntity<List<UsuarioResponse>> getAllUsuarios() {
        List<UsuarioResponse> usuarios = usuarioService.getAllUsuarios().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(usuarios);
        }

    @PostMapping
    public ResponseEntity<?> createUsuario(@RequestBody RegisterRequest request) {
        try {
            Usuario nuevoUsuario = usuarioService.registrarUsuario(request, request.getRole());
            return new ResponseEntity<>(mapToResponse(nuevoUsuario), HttpStatus.CREATED);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{id}/role")
    public ResponseEntity<?> cambiarRol(@PathVariable Integer id, @RequestBody Map<String, String> request) {
        try {
            Usuario usuario = usuarioService.cambiarRol(id, request.get("role"));
            return ResponseEntity.ok(mapToResponse(usuario));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    private UsuarioResponse mapToResponse(Usuario usuario) {
        return new UsuarioResponse(
                usuario.getId(),
                usuario.getName(),
                usuario.getLastname(),
                usuario.getEmail(),
                usuario.getRole(),
                usuario.getPhone(),
                usuario.getAddress(),
                usuario.getCommune()
        );
    }
}
