package duocuc.cl.rodrigo.carniverocrud.controller;


import duocuc.cl.rodrigo.carniverocrud.models.request.CompraRequest;
import duocuc.cl.rodrigo.carniverocrud.service.CompraService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/purchase/api")
public class CompraController {
    @Autowired
    private CompraService compraService;

    @PostMapping
    public ResponseEntity<?> createPurchase(@RequestBody CompraRequest compraRequest) {
        try{
            return ResponseEntity.ok(compraService.registerPurchase(compraRequest));
        }catch(RuntimeException e){
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
    @GetMapping("/{id}")
    public ResponseEntity<?> getPurchasebyId(@PathVariable Integer id) {
        try{
            return ResponseEntity.ok(compraService.getPurchase(id));
        }catch(RuntimeException e){
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
    @GetMapping
    public ResponseEntity<?> getAllPurchases() {
        return ResponseEntity.ok(compraService.getAllPurchases());
    }

}
