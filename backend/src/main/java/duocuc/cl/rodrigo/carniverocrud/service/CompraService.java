package duocuc.cl.rodrigo.carniverocrud.service;

import duocuc.cl.rodrigo.carniverocrud.models.entity.Compra;
import duocuc.cl.rodrigo.carniverocrud.models.entity.Detalle_Compra;
import duocuc.cl.rodrigo.carniverocrud.models.entity.Planta;
import duocuc.cl.rodrigo.carniverocrud.models.entity.Usuario;
import duocuc.cl.rodrigo.carniverocrud.models.request.CompraRequest;
import duocuc.cl.rodrigo.carniverocrud.models.request.ItemRequest;
import duocuc.cl.rodrigo.carniverocrud.repository.CompraJpaRepository;
import duocuc.cl.rodrigo.carniverocrud.repository.DetalleCompraJpaRepository;
import duocuc.cl.rodrigo.carniverocrud.repository.PlantaJpaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import duocuc.cl.rodrigo.carniverocrud.repository.UsuarioJpaRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CompraService {
    @Autowired
    private CompraJpaRepository compraJpaRepository;
    @Autowired
    private PlantaJpaRepository plantaJpaRepository;
    @Autowired
    private DetalleCompraJpaRepository detalleCompraJpaRepository;
    @Autowired
    private UsuarioJpaRepository usuarioJpaRepository;
    @Autowired 
    private EmailService emailService;

    @Transactional
    public Map<String, Object> registerPurchase(CompraRequest request, Authentication authentication) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("El carrito no puede estar vacío");
        }

        // 1. Obtener el usuario completo para sacar su Email y su ID
        Usuario usuario = usuarioJpaRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no encontrado"));
        String compradorId = usuario.getId().toString();
        String emailDestino = usuario.getEmail();

        // 2. Crear la compra base
        Compra nuevaCompra = new Compra();
        nuevaCompra.setIdUser(compradorId);
        nuevaCompra.setEstado("PENDIENTE");
        Compra compraGuardada = compraJpaRepository.save(nuevaCompra);

        // 3. Procesar cada item del carrito
        List<Detalle_Compra> listaDetalles = new ArrayList<>();
        List<Planta> plantasCompradas = new ArrayList<>();
        int totalCompra = 0;

        for (ItemRequest item : request.getItems()) {
            if (item.getIdPlanta() == null || item.getIdPlanta() <= 0) {
                throw new IllegalArgumentException("Debes indicar una planta válida");
            }
            if (item.getCantidad() == null || item.getCantidad() <= 0) {
                throw new IllegalArgumentException("La cantidad debe ser mayor a 0");
            }

            Planta planta = plantaJpaRepository.findById(item.getIdPlanta())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Planta no encontrada"));
            
            if (planta.getStock() < item.getCantidad()) {
                throw new IllegalArgumentException("Stock insuficiente para: " + planta.getName());
            }

            // Descontar stock
            planta.setStock(planta.getStock() - item.getCantidad());
            plantaJpaRepository.save(planta);

            // Crear el detalle
            Detalle_Compra detalle = new Detalle_Compra();
            detalle.setId_compra(compraGuardada.getId());
            detalle.setId_planta(planta.getId());
            detalle.setCantidad(item.getCantidad());
            detalle.setPrecio(planta.getPrice());

            Detalle_Compra detalleGuardado = detalleCompraJpaRepository.save(detalle);
            
            listaDetalles.add(detalleGuardado);
            plantasCompradas.add(planta);
            totalCompra += (planta.getPrice() * item.getCantidad());
        }

        // 4. Enviar el correo
        try {
            emailService.enviarConfirmacionCompra(emailDestino, compraGuardada, listaDetalles, plantasCompradas, totalCompra);
        } catch (Exception e) {
            System.err.println("Error enviando correo: " + e.getMessage());
        }

        // 5. Retornar la respuesta (adaptada para listas)
        return mapPurchaseMultiple(compraGuardada, listaDetalles, totalCompra);
    }
    public Map<String, Object> getPurchase(Integer id, Authentication authentication) {
        Compra compra = compraJpaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Compra no encontrada"));
        String userId = authenticatedUserId(authentication);
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
        if (!admin && !userId.equals(compra.getIdUser())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puedes consultar compras de otro usuario");
        }

        Detalle_Compra detalle = detalleCompraJpaRepository.findAll().stream()
                .filter(d -> d.getId_compra() == compra.getId())
                .findFirst()
                .orElse(null);

        return mapPurchase(compra, detalle);
    }

    private String authenticatedUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Debes iniciar sesion");
        }
        return usuarioJpaRepository.findByEmail(authentication.getName())
                .map(usuario -> usuario.getId().toString())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no encontrado"));
    }

    public List<Map<String, Object>> getAllPurchases() {
        List<Compra> compras = compraJpaRepository.findAll();
        Map<Integer, List<Detalle_Compra>> detallesPorCompra = detalleCompraJpaRepository.findAll().stream()
                .collect(Collectors.groupingBy(Detalle_Compra::getId_compra));

        List<Map<String, Object>> response = new ArrayList<>();
        for (Compra compra : compras) {
            List<Detalle_Compra> detalles = detallesPorCompra.get(compra.getId());
            if (detalles == null || detalles.isEmpty()) {
                response.add(mapPurchase(compra, null));
                continue;
            }

            for (Detalle_Compra detalle : detalles) {
                response.add(mapPurchase(compra, detalle));
            }
        }

        return response;
    }

    private Map<String, Object> mapPurchase(Compra compra, Detalle_Compra detalle) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", compra.getId());
        response.put("idCompra", compra.getId());
        response.put("idUser", compra.getIdUser());
        response.put("purchasedate", compra.getPurchasedate());
        response.put("purchaseDate", compra.getPurchasedate());
        response.put("estado", compra.getEstado());

        if (detalle != null) {
            response.put("idDetalle", detalle.getId_detalle());
            response.put("idPlanta", detalle.getId_planta());
            response.put("quantity", detalle.getCantidad());
            response.put("price", detalle.getPrecio());
            response.put("total", detalle.getPrecio() * detalle.getCantidad());
        }

        return response;
    }
    private Map<String, Object> mapPurchaseMultiple(Compra compra, List<Detalle_Compra> detalles, int total) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("idCompra", compra.getId());
        response.put("estado", compra.getEstado());
        response.put("totalCompra", total);
        
        List<Map<String, Object>> detallesList = detalles.stream().map(d -> {
            Map<String, Object> det = new LinkedHashMap<>();
            det.put("idPlanta", d.getId_planta());
            det.put("cantidad", d.getCantidad());
            det.put("subtotal", d.getPrecio() * d.getCantidad());
            return det;
        }).collect(Collectors.toList());
        
        response.put("detalles", detallesList);
        return response;
    }
}
