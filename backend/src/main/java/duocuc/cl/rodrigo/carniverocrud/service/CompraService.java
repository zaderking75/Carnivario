package duocuc.cl.rodrigo.carniverocrud.service;

import duocuc.cl.rodrigo.carniverocrud.models.entity.Compra;
import duocuc.cl.rodrigo.carniverocrud.models.request.CompraRequest;
import duocuc.cl.rodrigo.carniverocrud.repository.CompraJpaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompraService {
    @Autowired
    private CompraJpaRepository compraJpaRepository;

    @Transactional
    public Compra registerPurchase(CompraRequest request) {
        Compra nuevaCompra = new Compra();
        nuevaCompra.setIdUser(request.getIdUser());
        nuevaCompra.setEstado(
                request.getEstado() == null || request.getEstado().isBlank()
                        ? "PENDIENTE"
                        : request.getEstado()
        );

        return compraJpaRepository.save(nuevaCompra);
    }

    public Compra getPurchase(Integer id) {
        return compraJpaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Compra con el id: " + id + " no existe."));
    }
}
