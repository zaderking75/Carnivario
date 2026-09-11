package duocuc.cl.rodrigo.carniverocrud.service;


import duocuc.cl.rodrigo.carniverocrud.models.entity.Planta;
import duocuc.cl.rodrigo.carniverocrud.models.request.PlantaRequest;
import duocuc.cl.rodrigo.carniverocrud.repository.PlantaJpaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
        Optional<Planta> plantabuscar=plantaJpaRepository.findByName(planta.getName());
        if(plantabuscar.isPresent()) {
            throw new RuntimeException("Planta ya existe");
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
    public Planta addstockplanta(int stock,int plantaid) {
        Planta planta = plantaJpaRepository.findById(plantaid)
                .orElseThrow(() -> new RuntimeException("Planta no encontrada con id: " + plantaid));
        int nuevoStock = planta.getStock() + stock;
        planta.setStock(nuevoStock);

        return plantaJpaRepository.save(planta);
    }
    public List<Planta> getAllPlantas() {
        return plantaJpaRepository.findAll();
    }
    public Planta getPlantaById(int id) {
        return plantaJpaRepository.findById(id).orElseThrow(() -> new RuntimeException("Planta no encontrada con id: " + id));

    }
    public boolean deletePlantaById(int id) {
        Optional<Planta> plantabuscar=plantaJpaRepository.findById(id);
        if(plantabuscar.isPresent()) {
            plantaJpaRepository.delete(plantabuscar.get());
            return true;
        }
        return false;
    }
}
