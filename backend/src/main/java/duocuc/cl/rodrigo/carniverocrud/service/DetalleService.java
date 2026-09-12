package duocuc.cl.rodrigo.carniverocrud.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import duocuc.cl.rodrigo.carniverocrud.models.entity.Compra;
import duocuc.cl.rodrigo.carniverocrud.models.entity.Detalle_Compra;
import duocuc.cl.rodrigo.carniverocrud.models.entity.Planta;
import duocuc.cl.rodrigo.carniverocrud.models.request.DetalleCompraRequest;
import duocuc.cl.rodrigo.carniverocrud.repository.CompraJpaRepository;
import duocuc.cl.rodrigo.carniverocrud.repository.DetalleCompraJpaRepository;
import duocuc.cl.rodrigo.carniverocrud.repository.PlantaJpaRepository;
import jakarta.transaction.Transactional;

@Service
public class DetalleService {
    @Autowired
    private DetalleCompraJpaRepository detalleCompraJpaRepository;
    @Autowired 
    private CompraJpaRepository compraJpaRepository;
    @Autowired
    private PlantaJpaRepository plantaJpaRepository;
    @Transactional 
    public Detalle_Compra registerDetail(DetalleCompraRequest request) {

        Compra compra = compraJpaRepository.findById(request.getIdCompra())
                .orElseThrow(() -> new RuntimeException("Compra no encontrada"));

        Planta planta = plantaJpaRepository.findById(request.getIdPlanta())
                .orElseThrow(() -> new RuntimeException("Planta no encontrada"));

        if (planta.getStock() < request.getCantidad()) {
            throw new RuntimeException("Stock insuficiente.");
        }

        planta.setStock(planta.getStock() - request.getCantidad());
        plantaJpaRepository.save(planta);

        Detalle_Compra nuevoDetalle = new Detalle_Compra();
        nuevoDetalle.setId_compra(compra.getId());
        nuevoDetalle.setId_planta(planta.getId());
        nuevoDetalle.setCantidad(request.getCantidad());
        nuevoDetalle.setPrecio(planta.getPrice());

        return detalleCompraJpaRepository.save(nuevoDetalle);
    }

    public Detalle_Compra getDetail(Integer id) {
        return detalleCompraJpaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Detalle con el id: " + id + " no existe."));
    }
}

