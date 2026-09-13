package duocuc.cl.rodrigo.carniverocrud.controller;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import duocuc.cl.rodrigo.carniverocrud.models.request.CompraRequest;
import duocuc.cl.rodrigo.carniverocrud.service.CompraService;

@RestController
@RequestMapping("/purchase/api")
public class CompraController {
    @Autowired
    private CompraService compraService;

    @PostMapping
    public ResponseEntity<?> createPurchase(@RequestBody CompraRequest request, Authentication authentication) {
        return ResponseEntity.ok(compraService.registerPurchase(request, authentication));
    }
    @GetMapping("/{id}")
    public ResponseEntity<?> getPurchasebyId(@PathVariable Integer id, Authentication authentication) {
        return ResponseEntity.ok(compraService.getPurchase(id, authentication));
    }
    @GetMapping
    public ResponseEntity<?> getAllPurchases() {
        return ResponseEntity.ok(compraService.getAllPurchases());
    }

}
