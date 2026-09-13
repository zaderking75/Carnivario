package duocuc.cl.rodrigo.carniverocrud.service;

import duocuc.cl.rodrigo.carniverocrud.models.entity.Planta;
import duocuc.cl.rodrigo.carniverocrud.models.request.PlantaRequest;
import duocuc.cl.rodrigo.carniverocrud.repository.PlantaJpaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

@Service
public class PlantaService {
    @Autowired
    private PlantaJpaRepository plantaJpaRepository;

    public PlantaJpaRepository getPlantaJpaRepository() {
        return plantaJpaRepository;
    }

    public Planta registerNewPlanta(PlantaRequest planta) {
    public Planta registerNewPlanta(PlantaRequest planta) {
        validatePlanta(planta);

        Optional<Planta> plantabuscar = plantaJpaRepository.findByName(planta.getName());
        if (plantabuscar.isPresent()) {
            throw new IllegalArgumentException("Planta ya existe");
        }

        Planta plantaDB = new Planta();
        plantaDB.setName(planta.getName());
        plantaDB.setPrice(planta.getPrice());
        plantaDB.setDescription(planta.getDescription());
        plantaDB.setImage(planta.getImage());
        plantaDB.setPlanting(planta.getPlanting());
        plantaDB.setSize(planta.getSize());
        plantaDB.setStock(planta.getStock());
        return plantaJpaRepository.save(plantaDB);
    }

    public Planta addstockplanta(int stock, int plantaid) {
        if (stock == 0) {
            throw new IllegalArgumentException("La cantidad de ajuste no puede ser 0");
        }

        Planta planta = plantaJpaRepository.findById(plantaid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Planta no encontrada con id: " + plantaid));
        int nuevoStock = planta.getStock() + stock;
        if (nuevoStock < 0) {
            throw new IllegalArgumentException("El stock final no puede ser negativo");
        }
        planta.setStock(nuevoStock);
        return plantaJpaRepository.save(planta);
    }

    public Planta updatePlanta(int id, PlantaRequest request) {
        validatePlanta(request);

        Planta planta = plantaJpaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Planta no encontrada"));
        Optional<Planta> plantaConMismoNombre = plantaJpaRepository.findByName(request.getName());
        if (plantaConMismoNombre.isPresent() && !plantaConMismoNombre.get().getId().equals(id)) {
            throw new IllegalArgumentException("Planta ya existe");
        }

        planta.setName(request.getName());
        planta.setPrice(request.getPrice());
        planta.setDescription(request.getDescription());
        planta.setImage(request.getImage());
        planta.setPlanting(request.getPlanting());
        planta.setSize(request.getSize());
        planta.setStock(request.getStock());
        return plantaJpaRepository.save(planta);
    }

    public List<Planta> getAllPlantas() {
        return plantaJpaRepository.findAll();
    }

    public Planta getPlantaById(int id) {
        return plantaJpaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Planta no encontrada con id: " + id));
    }

    public boolean deletePlantaById(int id) {
        Optional<Planta> plantabuscar = plantaJpaRepository.findById(id);
        if (plantabuscar.isPresent()) {
            plantaJpaRepository.delete(plantabuscar.get());
            return true;
        }
        return false;
    }
    private void validatePlanta(PlantaRequest planta) {
        if (planta.getPrice() <= 0) {
            throw new IllegalArgumentException("El precio debe ser mayor a 0");
        }
        if (planta.getStock() < 0) {
            throw new IllegalArgumentException("El stock no puede ser negativo");
        }
    }
}
