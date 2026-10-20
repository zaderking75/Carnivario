package duocuc.cl.rodrigo.carnivariorabbitadmin.controller;

import duocuc.cl.rodrigo.carnivariorabbitadmin.dto.BindingRequest;
import duocuc.cl.rodrigo.carnivariorabbitadmin.dto.ExchangeRequest;
import duocuc.cl.rodrigo.carnivariorabbitadmin.dto.QueueRequest;
import duocuc.cl.rodrigo.carnivariorabbitadmin.service.RabbitAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rabbit")
@RequiredArgsConstructor
public class RabbitAdminController {

    private final RabbitAdminService service;

    @PostMapping("/queues")
    @ResponseStatus(HttpStatus.CREATED)
    public void createQueue(@Valid @RequestBody QueueRequest request) {
        service.createQueue(request);
    }

    @DeleteMapping("/queues/{name}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteQueue(@PathVariable String name) {
        service.deleteQueue(name);
    }

    @GetMapping("/queues/{name}")
    public RabbitAdminService.QueueState getQueue(@PathVariable String name) {
        return service.getQueue(name);
    }

    @PostMapping("/exchanges")
    @ResponseStatus(HttpStatus.CREATED)
    public void createExchange(@Valid @RequestBody ExchangeRequest request) {
        service.createExchange(request);
    }

    @DeleteMapping("/exchanges/{name}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteExchange(@PathVariable String name) {
        service.deleteExchange(name);
    }

    @PostMapping("/bindings")
    @ResponseStatus(HttpStatus.CREATED)
    public void createBinding(@Valid @RequestBody BindingRequest request) {
        service.createBinding(request);
    }

    @DeleteMapping("/bindings")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBinding(@Valid @RequestBody BindingRequest request) {
        service.deleteBinding(request);
    }
}
