package duocuc.cl.rodrigo.carniverocrud.controller;

import duocuc.cl.rodrigo.carniverocrud.models.entity.Compra;
import duocuc.cl.rodrigo.carniverocrud.models.request.CheckoutRequest;
import duocuc.cl.rodrigo.carniverocrud.service.CheckoutService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/checkout/api")
public class CheckoutController {

    @Autowired
    private CheckoutService checkoutService;

    @PostMapping
    public ResponseEntity<?> checkout(@RequestBody CheckoutRequest request, Authentication authentication) {
        try {
            String emailAutenticado = authentication.getName(); // viene del JWT, no del body
            Compra compra = checkoutService.procesarCheckout(emailAutenticado, request);
            return ResponseEntity.ok(compra);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }
}
