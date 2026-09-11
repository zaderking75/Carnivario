package duocuc.cl.rodrigo.carniverocrud.controller;


import duocuc.cl.rodrigo.carniverocrud.controller.response.PlantaResponse;
import duocuc.cl.rodrigo.carniverocrud.models.entity.Planta;
import duocuc.cl.rodrigo.carniverocrud.models.request.PlantaRequest;
import duocuc.cl.rodrigo.carniverocrud.service.PlantaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/planta/api")
public class PlantaController {
    @Autowired
    private PlantaService plantaService;

    @PutMapping("/{id}/{stock}")
    public ResponseEntity<?> addStock(@PathVariable int id,@PathVariable int stock){
        try{
            Planta plantaactualizada= plantaService.addstockplanta(stock,id);
            return ResponseEntity.ok(plantaactualizada);

        }catch(IllegalArgumentException e){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }
    @GetMapping
    public ResponseEntity<List<PlantaResponse>> getAllPlanta(){
        List<Planta> plantas=plantaService.getAllPlantas();
        List<PlantaResponse> plantasResponse=plantas.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(plantasResponse);
    }
    @GetMapping("/{id}")
    public ResponseEntity<?> getPlantaById(@PathVariable int id){
        Planta planta = plantaService.getPlantaById(id);
        return ResponseEntity.ok(mapToResponse(planta));
    }
    @PostMapping
    public ResponseEntity<?> registerPlanta(@RequestBody PlantaRequest request) {
        try {
            Planta planta = plantaService.registerNewPlanta(request);
            return new ResponseEntity<>(planta, HttpStatus.CREATED);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updatePlanta(@PathVariable int id, @RequestBody PlantaRequest request) {
        try {
            Planta planta = plantaService.updatePlanta(id, request);
            return ResponseEntity.ok(planta);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePlanta(@PathVariable Integer id) {
        boolean deleted = plantaService.deletePlantaById(id);
        if (deleted) {
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }





    private PlantaResponse mapToResponse(Planta db) {
        return new PlantaResponse(
                db.getId(),
                db.getName(),
                db.getPrice(),
                db.getImage(),
                db.getSize(),
                db.getPlanting(),
                db.getDescription(),
                db.getStock()
        );
    }


}
