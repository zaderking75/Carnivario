package duocuc.cl.rodrigo.carniverocrud.service;

import duocuc.cl.rodrigo.carniverocrud.models.entity.Compra;
import duocuc.cl.rodrigo.carniverocrud.models.entity.Detalle_Compra;
import duocuc.cl.rodrigo.carniverocrud.models.entity.Planta;
import duocuc.cl.rodrigo.carniverocrud.models.request.CompraRequest;
import duocuc.cl.rodrigo.carniverocrud.repository.CompraJpaRepository;
import duocuc.cl.rodrigo.carniverocrud.repository.DetalleCompraJpaRepository;
import duocuc.cl.rodrigo.carniverocrud.repository.PlantaJpaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional
    public Map<String, Object> registerPurchase(CompraRequest request) {
        Compra nuevaCompra = new Compra();
        nuevaCompra.setIdUser(request.getIdUser());
        nuevaCompra.setEstado(
                request.getEstado() == null || request.getEstado().isBlank()
                        ? "PENDIENTE"
                        : request.getEstado()
        );

        Compra compraGuardada = compraJpaRepository.save(nuevaCompra);

        if (request.getIdPlanta() == null) {
            return mapPurchase(compraGuardada, null);
        }

        if (request.getQuantity() == null || request.getQuantity() <= 0) {
            throw new RuntimeException("La cantidad debe ser mayor a 0");
        }

        Planta planta = plantaJpaRepository.findById(request.getIdPlanta())
                .orElseThrow(() -> new RuntimeException("Planta no encontrada"));

        if (planta.getStock() < request.getQuantity()) {
            throw new RuntimeException("Stock insuficiente.");
        }

        planta.setStock(planta.getStock() - request.getQuantity());
        plantaJpaRepository.save(planta);

        Detalle_Compra detalle = new Detalle_Compra();
        detalle.setId_compra(compraGuardada.getId());
        detalle.setId_planta(planta.getId());
        detalle.setCantidad(request.getQuantity());
        detalle.setPrecio(planta.getPrice());

        Detalle_Compra detalleGuardado = detalleCompraJpaRepository.save(detalle);
        return mapPurchase(compraGuardada, detalleGuardado);
    }

    public Map<String, Object> getPurchase(Integer id) {
        Compra compra = compraJpaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Compra con el id: " + id + " no existe."));

        Detalle_Compra detalle = detalleCompraJpaRepository.findAll().stream()
                .filter(d -> d.getId_compra() == compra.getId())
                .findFirst()
                .orElse(null);

        return mapPurchase(compra, detalle);
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
}
