package duocuc.cl.rodrigo.carniverocrud.controller;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import duocuc.cl.rodrigo.carniverocrud.controller.response.UsuarioResponse;
import duocuc.cl.rodrigo.carniverocrud.models.entity.Usuario;
import duocuc.cl.rodrigo.carniverocrud.models.request.AuthRequest;
import duocuc.cl.rodrigo.carniverocrud.models.request.RegisterRequest;
import duocuc.cl.rodrigo.carniverocrud.models.request.UpdateProfileRequest;
import duocuc.cl.rodrigo.carniverocrud.service.UsuarioService;

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
            String token = usuarioService.registrarUsuarioYGenerarToken(request);
            Usuario usuario = usuarioService.getUsuarioByEmail(request.getEmail());
            Map<String, Object> response = new HashMap<>();
            response.put("token", token);
            response.put("user", usuario);

            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @GetMapping("/me")
    public ResponseEntity<UsuarioResponse> getAuthenticatedUser(
            Authentication authentication
    ) {
        Usuario usuario = authentication instanceof JwtAuthenticationToken azure
                ? usuarioService.obtenerOCrearUsuarioMicrosoft(azure.getToken())
                : usuarioService.getUsuarioByEmail(authentication.getName());

        return ResponseEntity.ok(
                mapToResponse(usuario)
        );
    }

       @PutMapping("/update")
    public ResponseEntity<?> actualizarPerfil(
            Authentication authentication,
            @RequestBody UpdateProfileRequest request) {
        try {
            if (authentication instanceof JwtAuthenticationToken azure) {
                usuarioService.obtenerOCrearUsuarioMicrosoft(azure.getToken());
            }
            Usuario usuario = usuarioService.actualizarPerfil(authentication.getName(), request);
            return ResponseEntity.ok(mapToResponse(usuario));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Collections.singletonMap("message", e.getMessage()));
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
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteUsuario(@PathVariable Integer id) {
        try {
            usuarioService.getUsuarioById(id);
            usuarioService.deleteUsuario(id);
            return ResponseEntity.ok(Collections.singletonMap("message", "Usuario eliminado correctamente"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Collections.singletonMap("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> actualizarUsuarioAdmin(@PathVariable Integer id, @RequestBody RegisterRequest request) {
        try {
            Usuario actualizado = usuarioService.actualizarUsuarioPorId(id, request); 
            return ResponseEntity.ok(mapToResponse(actualizado));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Collections.singletonMap("error", e.getMessage()));
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
